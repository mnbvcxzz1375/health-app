"""
PII 蒸馏数据准备

三种来源：
1. Teacher 伪标注（用 OpenMed-PII 对未标注中文医疗文本做伪标注）
2. 合成模板数据（可控、量大，覆盖 PII 标签全集）
3. 合并 + 切分为 train/val/test CoNLL 格式

输出：
    distillation/pii/data/train.conll
    distillation/pii/data/val.conll
    distillation/pii/data/test.conll

【运行方式】
    # 仅合成数据（无需 GPU，快速跑通 pipeline）
    python distillation/pii/prepare_data.py \
        --output distillation/pii/data/ \
        --synth-count 8000

    # teacher 伪标注 + 合成数据
    python distillation/pii/prepare_data.py \
        --teacher OpenMed/OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1 \
        --source backend-java/src/main/java/com/ahealth/backend/consult/ \
        --output distillation/pii/data/ \
        --synth-count 4000 \
        --teacher-pseudo-count 6000
"""
from __future__ import annotations

import argparse
import json
import random
import re
from pathlib import Path
from typing import Iterable

# PII 标签集（与 OpenMed-PII teacher + backend-java PiiScrubService 对齐）
PII_LABELS = [
    "PERSON", "DOCTOR", "HOSPITAL", "DEPARTMENT",
    "PHONE", "ID_CARD", "ADDRESS", "EMAIL", "DATE", "AGE",
]


# ============================================================
# 1. 合成模板数据
# ============================================================

# 姓氏 + 名字
SURNAMES = list("赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜戚谢邹喻柏水窦章云苏潘葛奚范彭郎")
GIVEN_NAMES_M = ["伟", "强", "磊", "军", "洋", "勇", "艳", "杰", "涛", "明", "超", "霞", "平", "刚", "桂英"]
GIVEN_NAMES_F = ["芳", "娜", "敏", "静", "丽", "丹", "玲", "婷", "雪", "倩", "云", "颖", "莹", "琳", "楠"]

HOSPITALS = [
    "北京协和医院", "上海华山医院", "广州市第一人民医院", "北京大学第一医院",
    "浙江大学医学院附属第一医院", "华西医院", "中山大学附属第一医院",
    "复旦大学附属中山医院", "中南大学湘雅医院", "武汉同济医院",
]
DEPARTMENTS = ["心内科", "骨科", "神经外科", "呼吸科", "消化内科", "内分泌科", "肾内科", "普外科", "眼科", "耳鼻喉科"]

CITIES = ["北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "南京", "西安", "重庆"]
DISTRICTS = ["朝阳区", "海淀区", "徐汇区", "天河区", "福田区", "西湖区", "锦江区", "江汉区"]
ROADS = ["建国路", "中山路", "人民大道", "解放路", "长江路", "和平路", "文化路", "新华路"]

EMAIL_DOMAINS = ["example.com", "gmail.com", "qq.com", "163.com", "outlook.com", "126.com"]


def _gen_name() -> str:
    surname = random.choice(SURNAMES)
    given = random.choice(GIVEN_NAMES_M + GIVEN_NAMES_F)
    if random.random() < 0.3:
        given += random.choice(GIVEN_NAMES_M + GIVEN_NAMES_F)
    return surname + given


def _gen_phone() -> str:
    prefix = random.choice(["138", "139", "150", "158", "170", "180", "186", "189", "199"])
    body = "".join(random.choice("0123456789") for _ in range(8))
    return prefix + body


def _gen_id_card() -> str:
    # 简化版：6 位地区码 + 8 位生日 + 3 位顺序码 + 1 位校验（不严格校验）
    region = random.choice(["110101", "310101", "440101", "440301", "330101"])
    year = random.randint(1950, 2010)
    month = random.randint(1, 12)
    day = random.randint(1, 28)
    seq = f"{random.randint(0, 999):03d}"
    check = random.choice("0123456789X")
    return f"{region}{year}{month:02d}{day:02d}{seq}{check}"


def _gen_address() -> str:
    return f"{random.choice(CITIES)}{random.choice(DISTRICTS)}{random.choice(ROADS)}{random.randint(1, 999)}号"


def _gen_email() -> str:
    name = "".join(random.choice("abcdefghijklmnopqrstuvwxyz0123456789") for _ in range(random.randint(4, 10)))
    return f"{name}@{random.choice(EMAIL_DOMAINS)}"


def _gen_date() -> str:
    year = random.randint(2020, 2026)
    month = random.randint(1, 12)
    day = random.randint(1, 28)
    return f"{year}-{month:02d}-{day:02d}"


def _gen_age() -> str:
    return f"{random.randint(1, 95)}岁"


# 模板：占位符 → (生成函数, 标签)
TEMPLATES = [
    "患者{NAME}，{AGE}，因胸闷到{HOSPITAL}{DEPARTMENT}就诊，联系电话{PHONE}。",
    "{NAME}（{AGE}），身份证号{ID_CARD}，住址{ADDRESS}，主诉头痛 3 天。",
    "{DATE}，{DOCTOR}医生为患者{NAME}（{AGE}）开具检查单，邮箱{EMAIL}。",
    "患者{NAME}，{AGE}，到{HOSPITAL}{DEPARTMENT}复查，电话{PHONE}，地址{ADDRESS}。",
    "病例编号：{ID_CARD}，姓名{NAME}，{AGE}，就诊日期{DATE}，主治医师{DOCTOR}。",
    "{NAME} 男 {AGE}，{DATE}于{HOSPITAL}就诊，联系方式{PHONE}。",
    "请{NAME}于{DATE}到{DEPARTMENT}复诊，电话{PHONE}，邮箱{EMAIL}。",
    "患者{NAME}，{AGE}，住在{ADDRESS}，电话{PHONE}，紧急联系{DOCTOR}医生。",
    "{DOCTOR}医生在{HOSPITAL}{DEPARTMENT}出诊，时间{DATE}，预约电话{PHONE}。",
    "{NAME}（{ID_CARD}），{AGE}，{EMAIL}，就诊于{HOSPITAL}。",
    "复诊患者{NAME}，{AGE}，{DATE}到{DEPARTMENT}，地址{ADDRESS}。",
    "患者{NAME}咨询：{DATE}起咳嗽，{AGE}，{DOCTOR}医生建议查胸部 CT，电话{PHONE}。",
]

PLACEHOLDER_MAP = {
    "{NAME}": (_gen_name, "PERSON"),
    "{DOCTOR}": (lambda: _gen_name() + random.choice(["医生", "主任", "教授"]), "DOCTOR"),
    "{HOSPITAL}": (lambda: random.choice(HOSPITALS), "HOSPITAL"),
    "{DEPARTMENT}": (lambda: random.choice(DEPARTMENTS), "DEPARTMENT"),
    "{PHONE}": (_gen_phone, "PHONE"),
    "{ID_CARD}": (_gen_id_card, "ID_CARD"),
    "{ADDRESS}": (_gen_address, "ADDRESS"),
    "{EMAIL}": (_gen_email, "EMAIL"),
    "{DATE}": (_gen_date, "DATE"),
    "{AGE}": (_gen_age, "AGE"),
}


def synthesize_one() -> tuple[str, list[tuple[int, int, str]]]:
    """生成一条合成样本：返回 (text, [(start, end, label), ...])"""
    template = random.choice(TEMPLATES)
    text = template
    spans: list[tuple[int, int, str]] = []
    # 按出现顺序替换占位符（避免后位偏移）
    for placeholder, (gen_fn, label) in PLACEHOLDER_MAP.items():
        while placeholder in text:
            value = gen_fn()
            idx = text.find(placeholder)
            text = text[:idx] + value + text[idx + len(placeholder):]
            spans.append((idx, idx + len(value), label))
    # 按 start 排序，确保不重叠
    spans.sort(key=lambda x: x[0])
    return text, spans


# ============================================================
# 2. 中文分词（粗粒度：按字符）
# ============================================================

def text_to_char_tokens(text: str, spans: list[tuple[int, int, str]]) -> list[tuple[str, str]]:
    """
    将文本逐字分词，分配 BIO 标签。
    spans: [(start, end, label), ...] 已排序、不重叠
    返回 [(char, tag), ...]
    """
    tags: list[tuple[str, str]] = []
    span_idx = 0
    for i, ch in enumerate(text):
        # 跳过空白字符（CoNLL 中不表示）
        if ch.isspace():
            continue
        # 检查当前字符是否在某个 span 内
        tag = "O"
        while span_idx < len(spans) and spans[span_idx][1] <= i:
            span_idx += 1
        if span_idx < len(spans):
            start, end, label = spans[span_idx]
            if start <= i < end:
                tag = f"B-{label}" if i == start else f"I-{label}"
        tags.append((ch, tag))
    return tags


# ============================================================
# 3. CoNLL-2003 写入
# ============================================================

def write_conll(path: Path, samples: Iterable[tuple[str, list[tuple[int, int, str]]]]) -> int:
    """将样本写入 CoNLL 文件，返回样本数"""
    path.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with path.open("w", encoding="utf-8") as f:
        for text, spans in samples:
            tokens = text_to_char_tokens(text, spans)
            if not tokens:
                continue
            for ch, tag in tokens:
                f.write(f"{ch} {tag}\n")
            f.write("\n")  # 句子分隔
            count += 1
    return count


# ============================================================
# 4. Teacher 伪标注
# ============================================================

def pseudo_annotate_with_teacher(
    teacher_model: str,
    texts: list[str],
    device: str = "cuda:0",
    batch_size: int = 8,
) -> list[tuple[str, list[tuple[int, int, str]]]]:
    """
    用 teacher pipeline 对未标注文本做伪标注。
    返回 [(text, spans), ...]
    """
    try:
        from transformers import AutoTokenizer, AutoModelForTokenClassification, pipeline
    except ImportError as e:
        print(f"[ERROR] transformers 未安装: {e}", flush=True)
        return []

    print(f"[INFO] 加载 teacher: {teacher_model}")
    tokenizer = AutoTokenizer.from_pretrained(teacher_model)
    model = AutoModelForTokenClassification.from_pretrained(teacher_model).to(device).eval()
    pipe = pipeline(
        "token-classification",
        model=model,
        tokenizer=tokenizer,
        aggregation_strategy="simple",
        device=device,
    )

    out: list[tuple[str, list[tuple[int, int, str]]]] = []
    for i in range(0, len(texts), batch_size):
        batch = texts[i:i + batch_size]
        try:
            results = pipe(batch)
        except Exception as e:
            print(f"[WARN] teacher batch {i} 失败: {e}", flush=True)
            continue
        # pipeline 对 list 输入返回 list of list
        if isinstance(results, list) and results and isinstance(results[0], list):
            iter_results = results
        else:
            iter_results = [results]
        for text, res in zip(batch, iter_results):
            spans = []
            for ent in res:
                start = ent.get("start", 0)
                end = ent.get("end", 0)
                label = ent.get("entity_group", ent.get("entity", ""))
                if end > start and label in PII_LABELS:
                    spans.append((start, end, label))
            spans.sort(key=lambda x: x[0])
            # 去重叠
            dedup: list[tuple[int, int, str]] = []
            for s in spans:
                if dedup and s[0] < dedup[-1][1]:
                    continue
                dedup.append(s)
            out.append((text, dedup))
    print(f"[INFO] teacher 伪标注完成: {len(out)} 条")
    return out


def collect_source_texts(source_dirs: list[Path]) -> list[str]:
    """从 Java 源码中提取中文医疗咨询样本文本"""
    texts: list[str] = []
    pattern = re.compile(r'"[^"]*[\u4e00-\u9fa5][^"]*"')
    for d in source_dirs:
        if not d.exists():
            continue
        for f in d.rglob("*.java"):
            try:
                content = f.read_text(encoding="utf-8", errors="ignore")
            except Exception:
                continue
            for m in pattern.finditer(content):
                s = m.group(0)[1:-1].strip()
                if 10 <= len(s) <= 200 and any(k in s for k in ["患者", "医生", "症状", "诊断", "治疗", "处方"]):
                    texts.append(s)
    # 去重
    return list(dict.fromkeys(texts))


# ============================================================
# 5. 主流程
# ============================================================

def main() -> int:
    p = argparse.ArgumentParser(description="PII 蒸馏数据准备")
    p.add_argument("--teacher", type=str, default=None,
                   help="teacher 模型 ID（如 OpenMed/OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1）")
    p.add_argument("--source", type=str, nargs="*", default=[],
                   help="Java 源码目录（用于提取医疗咨询样本做伪标注）")
    p.add_argument("--output", type=str, default="distillation/pii/data/",
                   help="输出目录")
    p.add_argument("--synth-count", type=int, default=8000,
                   help="合成样本数（默认 8000）")
    p.add_argument("--teacher-pseudo-count", type=int, default=0,
                   help="teacher 伪标注样本上限（默认 0，即不使用 teacher）")
    p.add_argument("--device", type=str, default="cuda:0")
    p.add_argument("--seed", type=int, default=42)
    args = p.parse_args()

    random.seed(args.seed)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    samples: list[tuple[str, list[tuple[int, int, str]]]] = []

    # 1) 合成数据
    print(f"[INFO] 生成 {args.synth_count} 条合成样本...")
    for _ in range(args.synth_count):
        samples.append(synthesize_one())

    # 2) Teacher 伪标注
    if args.teacher and args.teacher_pseudo_count > 0:
        source_dirs = [Path(s) for s in args.source]
        texts = collect_source_texts(source_dirs)
        print(f"[INFO] 从 {source_dirs} 提取到 {len(texts)} 条源文本")
        if len(texts) > args.teacher_pseudo_count:
            texts = random.sample(texts, args.teacher_pseudo_count)
        if texts:
            pseudo = pseudo_annotate_with_teacher(args.teacher, texts, device=args.device)
            samples.extend(pseudo)

    # 3) 切分 8:1:1
    random.shuffle(samples)
    n = len(samples)
    n_train = int(n * 0.8)
    n_val = int(n * 0.1)
    train = samples[:n_train]
    val = samples[n_train:n_train + n_val]
    test = samples[n_train + n_val:]

    print(f"[INFO] 共 {n} 条样本 → train={len(train)}, val={len(val)}, test={len(test)}")

    # 4) 写入 CoNLL
    c1 = write_conll(output_dir / "train.conll", train)
    c2 = write_conll(output_dir / "val.conll", val)
    c3 = write_conll(output_dir / "test.conll", test)
    print(f"[INFO] 写入完成: train={c1}, val={c2}, test={c3}")

    # 5) 标签清单
    labels_path = output_dir / "labels.json"
    labels_path.write_text(json.dumps(["O"] + [f"{p}-{l}" for l in PII_LABELS for p in ("B", "I")],
                                      ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[INFO] 标签清单写入: {labels_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
