"""
PII 蒸馏性能对比 benchmark

输出表格（CSV + Markdown + 图表）：
| 模型 | Precision | Recall | F1 | 参数量(M) | 推理延迟(ms/句) | 模型大小(MB) |

PII 标签集（与 OpenMed-PII teacher 对齐）：
PERSON / DOCTOR / HOSPITAL / DEPARTMENT / PHONE / ID_CARD / ADDRESS / EMAIL / DATE / AGE

【运行方式】
    python distillation/pii/benchmark.py \
        --teacher OpenMed/OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1 \
        --student distillation/pii/checkpoints/student_final/ \
        --data distillation/pii/data/ \
        --device cuda:0 \
        --output distillation/pii/results/
"""
from __future__ import annotations

import argparse
import csv
import json
import time
from pathlib import Path
from typing import Any

import numpy as np
import torch
from seqeval.metrics import precision_score, recall_score, f1_score
from seqeval.scheme import IOB2


def read_conll(path: Path) -> list[list[str]]:
    """读取 CoNLL，返回 [[tag, tag, ...], ...]（仅标签，按字符）"""
    samples: list[list[str]] = []
    current: list[str] = []
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
                current.append(parts[1])
    if current:
        samples.append(current)
    return samples


def read_conll_with_text(path: Path) -> list[list[tuple[str, str]]]:
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


def count_parameters(model: torch.nn.Module) -> float:
    return sum(p.numel() for p in model.parameters()) / 1e6


def get_model_size_mb(model_dir: str) -> float:
    """估算模型大小（safetensors / bin 文件大小总和）"""
    p = Path(model_dir)
    total = 0
    for f in p.iterdir():
        if f.suffix in (".bin", ".safetensors", ".pt"):
            total += f.stat().st_size
    return total / (1024 * 1024)


def predict_labels(
    model,
    tokenizer,
    samples: list[list[tuple[str, str]]],
    id2label: dict[int, str],
    device: torch.device,
    batch_size: int = 8,
    max_len: int = 256,
) -> list[list[str]]:
    """逐句推理，返回 [[pred_tag, ...], ...]（按 token 对齐）"""
    model.eval()
    preds: list[list[str]] = []

    for sample in samples:
        chars = [c for c, _ in sample]
        text = "".join(chars)
        enc = tokenizer(
            text,
            return_offsets_mapping=True,
            truncation=True,
            max_length=max_len,
            return_tensors="pt",
        )
        input_ids = enc["input_ids"].to(device)
        attention_mask = enc["attention_mask"].to(device)
        offsets = enc["offset_mapping"][0].tolist()

        with torch.no_grad():
            out = model(input_ids=input_ids, attention_mask=attention_mask, return_dict=True)
            logits = out.logits  # (1, L, num_labels)
            pred_ids = logits.argmax(dim=-1)[0].cpu().tolist()

        # 把 token-level 预测按 offset 回填到字符级
        char_preds = ["O"] * len(chars)
        for tok_idx, (start, end) in enumerate(offsets):
            if start == end:
                continue
            if tok_idx >= len(pred_ids):
                continue
            label = id2label.get(pred_ids[tok_idx], "O")
            for c_idx in range(start, min(end, len(chars))):
                if c_idx == start:
                    char_preds[c_idx] = label
                else:
                    # 把 I- 替换为 B- 后续位置
                    if label.startswith("B-"):
                        char_preds[c_idx] = "I-" + label[2:]
                    elif label.startswith("I-"):
                        char_preds[c_idx] = label
                    else:
                        char_preds[c_idx] = "O"
        preds.append(char_preds)

    return preds


def measure_latency(
    model,
    tokenizer,
    samples: list[list[tuple[str, str]]],
    device: torch.device,
    n_warmup: int = 5,
    n_runs: int = 30,
    max_len: int = 256,
) -> float:
    """测量平均推理延迟（ms/句）"""
    model.eval()
    # 准备 batch
    texts = ["".join([c for c, _ in s]) for s in samples[:n_warmup + n_runs]]
    if not texts:
        return 0.0

    encodings = [tokenizer(t, truncation=True, max_length=max_len, return_tensors="pt") for t in texts]

    # warmup
    with torch.no_grad():
        for enc in encodings[:n_warmup]:
            _ = model(
                input_ids=enc["input_ids"].to(device),
                attention_mask=enc["attention_mask"].to(device),
                return_dict=True,
            )
    if device.type == "cuda":
        torch.cuda.synchronize()

    # measure
    started = time.perf_counter()
    with torch.no_grad():
        for enc in encodings[n_warmup:]:
            _ = model(
                input_ids=enc["input_ids"].to(device),
                attention_mask=enc["attention_mask"].to(device),
                return_dict=True,
            )
    if device.type == "cuda":
        torch.cuda.synchronize()
    elapsed = (time.perf_counter() - started) * 1000  # ms
    return elapsed / max(len(encodings) - n_warmup, 1)


def benchmark_model(
    name: str,
    model_dir: str,
    test_samples: list[list[tuple[str, str]]],
    true_tag_samples: list[list[str]],
    device: torch.device,
) -> dict[str, Any]:
    """评估单个模型"""
    print(f"\n[INFO] Benchmarking {name}: {model_dir}")
    try:
        from transformers import AutoTokenizer, AutoModelForTokenClassification
    except ImportError as e:
        print(f"[ERROR] {e}", flush=True)
        return {"name": name, "error": str(e)}

    tokenizer = AutoTokenizer.from_pretrained(model_dir)
    model = AutoModelForTokenClassification.from_pretrained(model_dir).to(device).eval()

    id2label = {i: l for i, l in enumerate(model.config.id2label.values())} if hasattr(model.config, "id2label") else {}
    if not id2label:
        # fallback：构建默认
        id2label = {i: f"LABEL_{i}" for i in range(model.config.num_labels)}

    # 推理
    pred_tag_samples = predict_labels(model, tokenizer, test_samples, id2label, device)

    # 评估
    p = precision_score(true_tag_samples, pred_tag_samples, mode="strict", scheme=IOB2)
    r = recall_score(true_tag_samples, pred_tag_samples, mode="strict", scheme=IOB2)
    f1 = f1_score(true_tag_samples, pred_tag_samples, mode="strict", scheme=IOB2)

    # 参数量 + 模型大小 + 延迟
    params_m = count_parameters(model)
    size_mb = get_model_size_mb(model_dir)
    latency_ms = measure_latency(model, tokenizer, test_samples, device)

    return {
        "name": name,
        "model_dir": model_dir,
        "params_m": round(params_m, 2),
        "size_mb": round(size_mb, 2),
        "latency_ms": round(latency_ms, 2),
        "precision": round(p, 4),
        "recall": round(r, 4),
        "f1": round(f1, 4),
    }


def write_results(results: list[dict[str, Any]], output_dir: Path) -> None:
    """输出 CSV + Markdown"""
    output_dir.mkdir(parents=True, exist_ok=True)

    # CSV
    csv_path = output_dir / "benchmark.csv"
    fields = ["name", "params_m", "size_mb", "latency_ms", "precision", "recall", "f1"]
    with csv_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        for r in results:
            if "error" not in r:
                writer.writerow({k: r.get(k, "") for k in fields})
    print(f"[INFO] CSV 写入: {csv_path}")

    # Markdown
    md_path = output_dir / "benchmark.md"
    with md_path.open("w", encoding="utf-8") as f:
        f.write("# PII 蒸馏性能对比\n\n")
        f.write("| 模型 | 参数量(M) | 模型大小(MB) | 推理延迟(ms/句) | Precision | Recall | F1 |\n")
        f.write("|------|-----------|--------------|-----------------|-----------|--------|----|\n")
        for r in results:
            if "error" in r:
                f.write(f"| {r['name']} | - | - | - | - | - | _error_ |\n")
            else:
                f.write(
                    f"| {r['name']} | {r['params_m']} | {r['size_mb']} | {r['latency_ms']} | "
                    f"{r['precision']} | {r['recall']} | {r['f1']} |\n"
                )
    print(f"[INFO] Markdown 写入: {md_path}")

    # 图表
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt

        names = [r["name"] for r in results if "error" not in r]
        f1s = [r["f1"] for r in results if "error" not in r]
        params = [r["params_m"] for r in results if "error" not in r]
        latencies = [r["latency_ms"] for r in results if "error" not in r]

        fig, axes = plt.subplots(1, 3, figsize=(15, 5))
        axes[0].bar(names, f1s, color=["#1f77b4", "#ff7f0e"][: len(names)])
        axes[0].set_title("F1 Score")
        axes[0].set_ylim(0, 1.0)
        for i, v in enumerate(f1s):
            axes[0].text(i, v + 0.02, f"{v:.4f}", ha="center")

        axes[1].bar(names, params, color=["#1f77b4", "#ff7f0e"][: len(names)])
        axes[1].set_title("Parameters (M)")
        for i, v in enumerate(params):
            axes[1].text(i, v + max(params) * 0.02, f"{v:.1f}", ha="center")

        axes[2].bar(names, latencies, color=["#1f77b4", "#ff7f0e"][: len(names)])
        axes[2].set_title("Latency (ms/sentence)")
        for i, v in enumerate(latencies):
            axes[2].text(i, v + max(latencies) * 0.02, f"{v:.2f}", ha="center")

        plt.tight_layout()
        fig_path = output_dir / "benchmark.png"
        plt.savefig(fig_path, dpi=120)
        print(f"[INFO] 图表写入: {fig_path}")
    except Exception as e:
        print(f"[WARN] 图表生成失败: {e}")


def main() -> int:
    p = argparse.ArgumentParser(description="PII 蒸馏 benchmark")
    p.add_argument("--teacher", type=str, required=True)
    p.add_argument("--student", type=str, required=True)
    p.add_argument("--data", type=str, default="distillation/pii/data/")
    p.add_argument("--device", type=str, default="cuda:0")
    p.add_argument("--output", type=str, default="distillation/pii/results/")
    args = p.parse_args()

    device = torch.device(args.device)
    data_dir = Path(args.data)
    output_dir = Path(args.output)

    # 读取测试集
    test_samples = read_conll_with_text(data_dir / "test.conll")
    true_tag_samples = read_conll(data_dir / "test.conll")
    print(f"[INFO] 测试集: {len(test_samples)} 句")

    if not test_samples:
        print("[ERROR] 测试集为空，请先运行 prepare_data.py")
        return 1

    results: list[dict[str, Any]] = []

    # Teacher
    try:
        t_res = benchmark_model("OpenMed-PII (teacher)", args.teacher, test_samples, true_tag_samples, device)
        results.append(t_res)
    except Exception as e:
        print(f"[ERROR] teacher benchmark 失败: {e}")
        results.append({"name": "OpenMed-PII (teacher)", "error": str(e)})

    # Student
    try:
        s_res = benchmark_model("DistilBERT-chinese (student)", args.student, test_samples, true_tag_samples, device)
        results.append(s_res)
    except Exception as e:
        print(f"[ERROR] student benchmark 失败: {e}")
        results.append({"name": "DistilBERT-chinese (student)", "error": str(e)})

    # 输出
    write_results(results, output_dir)

    # 打印摘要
    print("\n" + "=" * 70)
    print("Benchmark Summary")
    print("=" * 70)
    for r in results:
        if "error" in r:
            print(f"{r['name']}: ERROR ({r['error']})")
        else:
            print(f"{r['name']}: F1={r['f1']:.4f} | P={r['precision']:.4f} | R={r['recall']:.4f} | "
                  f"Params={r['params_m']}M | Size={r['size_mb']}MB | Latency={r['latency_ms']}ms")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
