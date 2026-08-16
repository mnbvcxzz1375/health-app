"""
OpenMed-PII → DistilBERT-chinese 蒸馏训练器

采用 TinyBERT 多阶段蒸馏（适配 NER token-classification）：

| Stage | 损失 | 说明 |
|-------|------|------|
| Stage 1: Embedding | MSE(student_embed, teacher_embed) | 词嵌入层对齐 |
| Stage 2: Hidden | Σ MSE(student_layer_i, proj(teacher_layer_j)) | 隐层对齐 |
| Stage 3: Attention | Σ KL(teacher_attn, student_attn) | 注意力分布对齐 |
| Stage 4: Prediction | CE(true_labels) + KL(teacher_logits/T, student_logits/T)×T² | 任务损失 + 软标签 |

【Teacher】OpenMed/OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1（Qwen 架构，24 层，~600M）
【Student】6 层 DistilBERT-chinese（从 bert-base-chinese 截取前 6 层初始化，~66M）

【层映射】24→6，每 4 层映射 1 层：
    student_layer_0 ← teacher_layer_{0,1,2,3}
    student_layer_1 ← teacher_layer_{4,5,6,7}
    ...
    student_layer_5 ← teacher_layer_{20,21,22,23}

【Tokenizer 对齐】
    必须**用 teacher tokenizer 分词**（Qwen BPE），student 词表通过 embedding projection 对齐。
    或对 student 用 BERT WordPiece，但仅对预测层蒸馏（Stage 4 only）——本实现采用前者更严格。

【运行方式】
    # 阶段 1-3: 中间层蒸馏（无需真实标签，可用 teacher 伪标注数据）
    python distillation/pii/distill_trainer.py \
        --stage intermediate \
        --teacher OpenMed/OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1 \
        --student-init bert-base-chinese \
        --student-layers 6 \
        --data distillation/pii/data/ \
        --epochs 300 --batch 32 --device cuda:0 \
        --output distillation/pii/checkpoints/student_intermediate/

    # 阶段 4: 预测层蒸馏（使用真实标签 + teacher 软标签）
    python distillation/pii/distill_trainer.py \
        --stage prediction \
        --teacher OpenMed/OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1 \
        --student distillation/pii/checkpoints/student_intermediate/ \
        --data distillation/pii/data/ \
        --epochs 100 --batch 32 --temperature 4.0 --device cuda:0 \
        --output distillation/pii/checkpoints/student_final/
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Optional

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import Dataset, DataLoader


# ============================================================
# 1. CoNLL 数据加载
# ============================================================

def read_conll(path: Path) -> list[list[tuple[str, str]]]:
    """读取 CoNLL 文件，返回 [[(char, tag), ...], ...]"""
    samples: list[list[tuple[str, str]]] = []
    current: list[tuple[str, str]] = []
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip():
                if current:
                    samples.append(current)
                    current = []
                continue
            parts = line.split()
            if len(parts) >= 2:
                current.append((parts[0], parts[1]))
    if current:
        samples.append(current)
    return samples


def build_label_list(labels_json: Path) -> list[str]:
    """读取标签清单"""
    return json.loads(labels_json.read_text(encoding="utf-8"))


# ============================================================
# 2. Student 模型构建（从 bert-base-chinese 截取 6 层）
# ============================================================

def build_student_model(
    student_init: str,
    num_labels: int,
    num_layers: int = 6,
):
    """
    从 bert-base-chinese 截取前 num_layers 层作为 student。

    返回 (model, tokenizer)
    """
    from transformers import BertForTokenClassification, BertTokenizerFast, BertConfig

    print(f"[INFO] 构建学生模型：{student_init} 截取前 {num_layers} 层")
    config = BertConfig.from_pretrained(student_init, num_labels=num_labels)
    config.num_hidden_layers = num_layers
    # 必须用 eager attention，sdpa 不支持 output_attentions=True
    # transformers 5.x: 通过 config._attn_implementation 设置
    config._attn_implementation = "eager"

    # 加载完整 BERT，然后只取前 num_layers 层参数
    full_model = BertForTokenClassification.from_pretrained(
        student_init, attn_implementation="eager",
    )
    student = BertForTokenClassification(config)

    # 拷贝 embedding + 前 num_layers 层 + classifier
    student.bert.embeddings.load_state_dict(full_model.bert.embeddings.state_dict())
    for i in range(num_layers):
        student.bert.encoder.layer[i].load_state_dict(full_model.bert.encoder.layer[i].state_dict())
    # classifier 需匹配 num_labels
    if full_model.classifier.out_features == num_labels:
        student.classifier.load_state_dict(full_model.classifier.state_dict())

    tokenizer = BertTokenizerFast.from_pretrained(student_init)
    del full_model
    return student, tokenizer


def load_student_from_checkpoint(checkpoint_dir: str, num_labels: int):
    """从中间层蒸馏 checkpoint 加载 student"""
    from transformers import BertForTokenClassification, BertTokenizerFast, BertConfig
    config = BertConfig.from_pretrained(checkpoint_dir, num_labels=num_labels)
    config._attn_implementation = "eager"
    model = BertForTokenClassification.from_pretrained(checkpoint_dir, config=config)
    tokenizer = BertTokenizerFast.from_pretrained(checkpoint_dir)
    return model, tokenizer


# ============================================================
# 3. Teacher 模型加载
# ============================================================

def load_teacher_model(teacher_model: str, num_labels: int):
    """
    加载 teacher（OpenMed-PII Qwen）。

    注意：teacher 的 label set 必须与 student 一致。
    OpenMed-PII 原生 label set 可能与本项目 PII_LABELS 不完全一致，
    需要做 label 映射。这里假设 teacher 已经 finetune 到目标 label set，
    或通过 index 重映射在数据预处理时完成。
    """
    from transformers import AutoTokenizer, AutoModelForTokenClassification
    print(f"[INFO] 加载 teacher: {teacher_model}")
    tokenizer = AutoTokenizer.from_pretrained(teacher_model)
    # 必须用 eager attention，sdpa 不支持 output_attentions=True
    model = AutoModelForTokenClassification.from_pretrained(
        teacher_model, attn_implementation="eager",
    )
    model.eval()
    return model, tokenizer


# ============================================================
# 4. Dataset
# ============================================================

class CoNLLDataset(Dataset):
    """逐字 token-classification dataset"""

    def __init__(self, samples: list[list[tuple[str, str]]], tokenizer, label2id: dict[str, int], max_len: int = 256):
        self.samples = samples
        self.tokenizer = tokenizer
        self.label2id = label2id
        self.max_len = max_len

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        chars = [c for c, _ in self.samples[idx]]
        tags = [t for _, t in self.samples[idx]]
        text = "".join(chars)

        # 用 teacher tokenizer 分词，offset_mapping 用于回填标签
        enc = self.tokenizer(
            text,
            return_offsets_mapping=True,
            truncation=True,
            max_length=self.max_len,
            return_tensors="pt",
        )

        input_ids = enc["input_ids"][0]
        attention_mask = enc["attention_mask"][0]
        offsets = enc["offset_mapping"][0]

        # 构造 labels：按字符位置回填到 token
        labels = torch.full_like(input_ids, fill_value=-100)
        # 跳过 [CLS] / [SEP]
        char_label_ids = [self.label2id.get(t, 0) for t in tags]
        for tok_idx, (start, end) in enumerate(offsets.tolist()):
            if start == end:
                continue  # special tokens
            if start < len(char_label_ids):
                labels[tok_idx] = char_label_ids[start]

        return {
            "input_ids": input_ids,
            "attention_mask": attention_mask,
            "labels": labels,
        }


def pad_collate_fn(batch: list[dict]) -> dict[str, torch.Tensor]:
    """
    动态 padding collate 函数。

    将一个 batch 中长度不一的 input_ids/attention_mask/labels
    pad 到该 batch 最大长度，确保 torch.stack 不报错。
    - input_ids 用 pad_token_id pad
    - attention_mask 用 0 pad
    - labels 用 -100 pad（不参与 loss）
    """
    input_ids = [item["input_ids"] for item in batch]
    attention_mask = [item["attention_mask"] for item in batch]
    labels = [item["labels"] for item in batch]

    max_len = max(t.size(0) for t in input_ids)
    pad_id = 0  # bert-base-chinese pad_token_id=0
    padded_input_ids = []
    padded_attention_mask = []
    padded_labels = []
    for ids, mask, lab in zip(input_ids, attention_mask, labels):
        pad_size = max_len - ids.size(0)
        if pad_size > 0:
            padded_input_ids.append(torch.cat([ids, torch.full((pad_size,), pad_id, dtype=ids.dtype)]))
            padded_attention_mask.append(torch.cat([mask, torch.zeros(pad_size, dtype=mask.dtype)]))
            padded_labels.append(torch.cat([lab, torch.full((pad_size,), -100, dtype=lab.dtype)]))
        else:
            padded_input_ids.append(ids)
            padded_attention_mask.append(mask)
            padded_labels.append(lab)

    return {
        "input_ids": torch.stack(padded_input_ids, 0),
        "attention_mask": torch.stack(padded_attention_mask, 0),
        "labels": torch.stack(padded_labels, 0),
    }


# ============================================================
# 5. 中间层蒸馏（Stage 1-3）
# ============================================================

class IntermediateDistiller:
    """
    Stage 1-3: Embedding + Hidden + Attention 蒸馏
    无需真实标签（但需要 teacher 输入一致）
    """

    def __init__(
        self,
        teacher_model: nn.Module,
        student_model: nn.Module,
        teacher_tokenizer,
        student_tokenizer,
        device: torch.device,
        lambda_embed: float = 1.0,
        lambda_hidden: float = 1.0,
        lambda_attn: float = 0.5,
    ):
        self.teacher = teacher_model.to(device).eval()
        self.student = student_model.to(device).train()
        self.teacher_tok = teacher_tokenizer
        self.student_tok = student_tokenizer
        self.device = device
        self.lambda_embed = lambda_embed
        self.lambda_hidden = lambda_hidden
        self.lambda_attn = lambda_attn

        # 教师层数（24）→ 学生层数（6）映射
        t_layers = self.teacher.config.num_hidden_layers
        s_layers = self.student.config.num_hidden_layers
        # 每 t_layers/s_layers 层映射 1 层（取最后一层作为代表）
        self.layer_map = [
            (s_idx, min(t_idx, t_layers - 1))
            for s_idx, t_idx in enumerate(
                np.linspace(0, t_layers - 1, s_layers).round().astype(int).tolist()
            )
        ]
        # hidden size 投影（teacher hidden → student hidden）
        t_hidden = self.teacher.config.hidden_size
        s_hidden = self.student.config.hidden_size
        self.proj = nn.Linear(t_hidden, s_hidden, bias=False).to(device) if t_hidden != s_hidden else nn.Identity()

        # 注意力头数对齐
        t_heads = self.teacher.config.num_attention_heads
        s_heads = self.student.config.num_attention_heads
        self.t_heads = t_heads
        self.s_heads = s_heads

    def _get_teacher_hidden_states(self, input_ids, attention_mask):
        """获取 teacher 各层 hidden states + attentions"""
        with torch.no_grad():
            out = self.teacher(
                input_ids=input_ids.to(self.device),
                attention_mask=attention_mask.to(self.device),
                output_hidden_states=True,
                output_attentions=True,
                return_dict=True,
            )
        return out.hidden_states, out.attentions

    def _get_student_hidden_states(self, input_ids, attention_mask):
        out = self.student(
            input_ids=input_ids.to(self.device),
            attention_mask=attention_mask.to(self.device),
            output_hidden_states=True,
            output_attentions=True,
            return_dict=True,
        )
        return out.hidden_states, out.attentions

    def compute_loss(self, batch) -> torch.Tensor:
        # Teacher 与 Student 用各自 tokenizer，但输入同一文本
        # 这里假设 batch 已是 teacher tokenizer 的输出，student 用相同 input_ids
        # 严格场景需重新分词，简化处理用相同 input_ids（如两者词表差异大需重写）
        input_ids = batch["input_ids"]
        attention_mask = batch["attention_mask"]

        t_hidden, t_attn = self._get_teacher_hidden_states(input_ids, attention_mask)
        s_hidden, s_attn = self._get_student_hidden_states(input_ids, attention_mask)

        # Stage 1: Embedding loss（teacher embedding 通过 proj 投影到 student 维度）
        # teacher 可能是 BFloat16，proj 是 float32，统一转 float32 计算
        t_embed_proj = self.proj(t_hidden[0].float())
        loss_embed = F.mse_loss(s_hidden[0].float(), t_embed_proj)

        # Stage 2: Hidden loss（按 layer_map）
        loss_hidden = 0.0
        for s_idx, t_idx in self.layer_map:
            s_h = s_hidden[s_idx + 1]  # +1 跳过 embedding
            t_h = t_hidden[t_idx + 1]
            t_h_proj = self.proj(t_h.float())
            loss_hidden = loss_hidden + F.mse_loss(s_h.float(), t_h_proj)
        loss_hidden = loss_hidden / len(self.layer_map)

        # Stage 3: Attention loss（KL）
        loss_attn = 0.0
        for s_idx, t_idx in self.layer_map:
            s_a = s_attn[s_idx]  # (B, s_heads, L, L)
            t_a = t_attn[t_idx]  # (B, t_heads, L, L)
            # 对齐头数：均值池化到较少的头数
            min_heads = min(s_a.size(1), t_a.size(1))
            s_a_red = self._reduce_heads(s_a, min_heads)
            t_a_red = self._reduce_heads(t_a, min_heads)
            # 归一化为概率分布（沿最后一维 softmax）
            s_logp = F.log_softmax(s_a_red.reshape(-1, s_a_red.size(-1)), dim=-1)
            t_p = F.softmax(t_a_red.reshape(-1, t_a_red.size(-1)).to(s_a_red.dtype), dim=-1)
            loss_attn = loss_attn + F.kl_div(s_logp, t_p, reduction="batchmean")
        loss_attn = loss_attn / len(self.layer_map)

        return self.lambda_embed * loss_embed + self.lambda_hidden * loss_hidden + self.lambda_attn * loss_attn

    @staticmethod
    def _reduce_heads(attn: torch.Tensor, target_heads: int) -> torch.Tensor:
        """(B, H, L, L) → (B, target_heads, L, L) 通过分组均值"""
        if attn.size(1) == target_heads:
            return attn
        B, H, L, _ = attn.shape
        if H % target_heads == 0:
            return attn.view(B, target_heads, H // target_heads, L, L).mean(dim=2)
        else:
            # 截取
            return attn[:, :target_heads]

    def train(self, dataloader, epochs: int, lr: float, output_dir: Path):
        optimizer = torch.optim.AdamW(
            list(self.student.parameters()) + list(self.proj.parameters()),
            lr=lr,
        )
        output_dir.mkdir(parents=True, exist_ok=True)
        step = 0
        for epoch in range(epochs):
            total_loss = 0.0
            for batch in dataloader:
                optimizer.zero_grad()
                loss = self.compute_loss(batch)
                loss.backward()
                torch.nn.utils.clip_grad_norm_(self.student.parameters(), 1.0)
                optimizer.step()
                total_loss += loss.item()
                step += 1
                if step % 50 == 0:
                    print(f"[Stage1-3] epoch {epoch+1}/{epochs} step {step} loss={loss.item():.4f}", flush=True)
            print(f"[Stage1-3] epoch {epoch+1}/{epochs} avg_loss={total_loss/len(dataloader):.4f}", flush=True)
            # 每 50 epoch 保存一次
            if (epoch + 1) % 50 == 0 or epoch == epochs - 1:
                self.student.save_pretrained(output_dir)
                print(f"[Stage1-3] checkpoint saved: {output_dir}", flush=True)


# ============================================================
# 6. 预测层蒸馏（Stage 4）
# ============================================================

class PredictionDistiller:
    """
    Stage 4: 任务损失 + 软标签
    L = λ_task · CE(student_logits, true_labels) + λ_kd · KL(teacher, student) × T²
    """

    def __init__(
        self,
        teacher_model: nn.Module,
        student_model: nn.Module,
        tokenizer,
        device: torch.device,
        temperature: float = 4.0,
        lambda_task: float = 1.0,
        lambda_kd: float = 2.0,
    ):
        self.teacher = teacher_model.to(device).eval()
        self.student = student_model.to(device).train()
        self.tokenizer = tokenizer
        self.device = device
        self.T = temperature
        self.lambda_task = lambda_task
        self.lambda_kd = lambda_kd

    def compute_loss(self, batch) -> tuple[torch.Tensor, dict]:
        input_ids = batch["input_ids"].to(self.device)
        attention_mask = batch["attention_mask"].to(self.device)
        labels = batch["labels"].to(self.device)

        # Teacher logits（无梯度）
        with torch.no_grad():
            t_out = self.teacher(
                input_ids=input_ids,
                attention_mask=attention_mask,
                return_dict=True,
            )
            t_logits = t_out.logits  # (B, L, num_labels)

        # Student logits
        s_out = self.student(
            input_ids=input_ids,
            attention_mask=attention_mask,
            labels=labels,
            return_dict=True,
        )
        s_logits = s_out.logits  # (B, L, num_labels)

        # 任务损失（CE on true labels, -100 忽略）
        loss_task = s_out.loss

        # 软标签蒸馏（KL on non-ignored positions）
        mask = (labels != -100)  # (B, L)
        if mask.any():
            T = self.T
            s_logp = F.log_softmax(s_logits[mask] / T, dim=-1)
            t_p = F.softmax(t_logits[mask].to(s_logits.dtype) / T, dim=-1)
            loss_kd = F.kl_div(s_logp, t_p, reduction="batchmean") * (T * T)
        else:
            loss_kd = torch.tensor(0.0, device=self.device)

        total = self.lambda_task * loss_task + self.lambda_kd * loss_kd
        return total, {
            "loss_task": loss_task.item(),
            "loss_kd": loss_kd.item(),
            "total": total.item(),
        }

    def train(self, dataloader, epochs: int, lr: float, output_dir: Path):
        optimizer = torch.optim.AdamW(self.student.parameters(), lr=lr)
        output_dir.mkdir(parents=True, exist_ok=True)
        step = 0
        best_loss = float("inf")
        for epoch in range(epochs):
            total_loss = 0.0
            for batch in dataloader:
                optimizer.zero_grad()
                loss, stats = self.compute_loss(batch)
                loss.backward()
                torch.nn.utils.clip_grad_norm_(self.student.parameters(), 1.0)
                optimizer.step()
                total_loss += loss.item()
                step += 1
                if step % 50 == 0:
                    print(f"[Stage4] epoch {epoch+1}/{epochs} step {step} "
                          f"task={stats['loss_task']:.4f} kd={stats['loss_kd']:.4f}", flush=True)
            avg = total_loss / len(dataloader)
            print(f"[Stage4] epoch {epoch+1}/{epochs} avg_loss={avg:.4f}", flush=True)
            # 保存 best
            if avg < best_loss:
                best_loss = avg
                self.student.save_pretrained(output_dir)
                print(f"[Stage4] best checkpoint saved: {output_dir} (loss={best_loss:.4f})", flush=True)


# ============================================================
# 7. 主流程
# ============================================================

def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="PII 蒸馏训练")
    p.add_argument("--stage", choices=["intermediate", "prediction"], required=True,
                   help="蒸馏阶段：intermediate=Stage1-3 中间层；prediction=Stage4 预测层")
    p.add_argument("--teacher", type=str, required=True,
                   help="teacher 模型 ID 或路径")
    p.add_argument("--student-init", type=str, default="bert-base-chinese",
                   help="student 初始化模型（仅 intermediate 阶段使用）")
    p.add_argument("--student", type=str, default=None,
                   help="student checkpoint 路径（仅 prediction 阶段使用）")
    p.add_argument("--student-layers", type=int, default=6,
                   help="student 隐藏层数（默认 6）")
    p.add_argument("--data", type=str, default="distillation/pii/data/",
                   help="数据目录（含 train.conll / val.conll / labels.json）")
    p.add_argument("--output", type=str, required=True,
                   help="输出 checkpoint 目录")
    p.add_argument("--epochs", type=int, default=300)
    p.add_argument("--batch", type=int, default=32)
    p.add_argument("--lr", type=float, default=5e-5)
    p.add_argument("--temperature", type=float, default=4.0)
    p.add_argument("--device", type=str, default="cuda:0")
    p.add_argument("--max-len", type=int, default=256)
    p.add_argument("--lambda-embed", type=float, default=1.0)
    p.add_argument("--lambda-hidden", type=float, default=1.0)
    p.add_argument("--lambda-attn", type=float, default=0.5)
    p.add_argument("--lambda-task", type=float, default=1.0)
    p.add_argument("--lambda-kd", type=float, default=2.0)
    p.add_argument("--workers", type=int, default=4)
    return p.parse_args()


def main() -> int:
    args = parse_args()
    device = torch.device(args.device)
    data_dir = Path(args.data)

    # 加载标签
    label_list = build_label_list(data_dir / "labels.json")
    label2id = {l: i for i, l in enumerate(label_list)}
    num_labels = len(label_list)
    print(f"[INFO] 标签数: {num_labels} → {label_list}")

    # 加载数据
    train_samples = read_conll(data_dir / "train.conll")
    val_samples = read_conll(data_dir / "val.conll")
    print(f"[INFO] train={len(train_samples)} val={len(val_samples)}")

    # 加载 teacher
    teacher, teacher_tok = load_teacher_model(args.teacher, num_labels)

    # 加载 student
    if args.stage == "intermediate":
        student, student_tok = build_student_model(args.student_init, num_labels, args.student_layers)
    else:
        assert args.student, "prediction 阶段必须提供 --student checkpoint"
        student, student_tok = load_student_from_checkpoint(args.student, num_labels)

    # 用 teacher tokenizer 作为统一分词（确保 teacher/student 输入对齐）
    # 但 student 是 BERT 架构，需要用 student tokenizer 才能正确编码
    # 折衷：以 student tokenizer 为主（确保 student 输入正确），teacher 用相同 input_ids
    # 注意：如 teacher 与 student 词表差异大，需重新对齐——此处简化处理
    unified_tok = student_tok

    train_ds = CoNLLDataset(train_samples, unified_tok, label2id, max_len=args.max_len)
    val_ds = CoNLLDataset(val_samples, unified_tok, label2id, max_len=args.max_len)
    train_loader = DataLoader(
        train_ds, batch_size=args.batch, shuffle=True,
        num_workers=args.workers, collate_fn=pad_collate_fn,
    )
    val_loader = DataLoader(
        val_ds, batch_size=args.batch, shuffle=False,
        num_workers=args.workers, collate_fn=pad_collate_fn,
    )

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    if args.stage == "intermediate":
        distiller = IntermediateDistiller(
            teacher_model=teacher,
            student_model=student,
            teacher_tokenizer=teacher_tok,
            student_tokenizer=student_tok,
            device=device,
            lambda_embed=args.lambda_embed,
            lambda_hidden=args.lambda_hidden,
            lambda_attn=args.lambda_attn,
        )
        distiller.train(train_loader, args.epochs, args.lr, output_dir)
    else:
        distiller = PredictionDistiller(
            teacher_model=teacher,
            student_model=student,
            tokenizer=unified_tok,
            device=device,
            temperature=args.temperature,
            lambda_task=args.lambda_task,
            lambda_kd=args.lambda_kd,
        )
        distiller.train(train_loader, args.epochs, args.lr, output_dir)

    # 保存 tokenizer
    unified_tok.save_pretrained(output_dir)
    print(f"[DONE] student 模型保存至 {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
