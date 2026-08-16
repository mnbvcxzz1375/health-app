"""
YOLO26 蒸馏性能对比 Benchmark

对比 Teacher（DINO-SO-YOLO）与 Student（YOLO26n 蒸馏后）的：
- mAP@0.5 / mAP@0.5:0.95
- 参数量（M）
- FLOPs（G）
- 推理延迟（ms/img，在指定 device 上）
- 模型大小（MB）

输出 CSV + Markdown 表格 + 对比柱状图（matplotlib）。

【运行方式】
    python distillation/yolo26/benchmark.py \
        --teacher local_medication_api/weights/yolov13n.pt \
        --student runs/distill/yolo26n_stage_b/weights/best.pt \
        --data distillation/yolo26/data/medical-pills.yaml \
        --device 0 --imgsz 640
"""
from __future__ import annotations

import argparse
import csv
import re
import sys
import time
import importlib
from pathlib import Path
from typing import Any

import numpy as np


# ============================================================
# 注入 vendored ultralytics 的自定义类到 site-packages（强制覆盖模式）
# ============================================================
def _inject_vendored_classes():
    """从 vendored nn/modules/*.py 提取 class 定义，强制注入到 site-packages 对应模块"""
    script_path = Path(__file__).resolve()
    project_root = script_path.parents[2]
    vendored_root = project_root / "local_medication_api" / "vendor" / "ultralytics" / "nn" / "modules"
    if not vendored_root.exists():
        vendored_root = Path("/data/hyc/local_medication_api/vendor/ultralytics/nn/modules")
    if not vendored_root.exists():
        return False

    site_pkg_prefix = "ultralytics.nn.modules"
    module_files = ["block.py", "conv.py", "head.py", "transformer.py", "utils.py"]
    injected_count = 0
    overwritten = []
    for mod_file in module_files:
        vendored_path = vendored_root / mod_file
        if not vendored_path.exists():
            continue
        mod_name = mod_file[:-3]
        full_mod_name = f"{site_pkg_prefix}.{mod_name}"
        try:
            site_mod = importlib.import_module(full_mod_name)
        except Exception:
            continue
        with vendored_path.open("r", encoding="utf-8") as f:
            src = f.read()
        pattern = r"(class \w+\b.*?)(?=\nclass |\Z)"
        classes = re.findall(pattern, src, re.DOTALL)
        for cls_src in classes:
            name_match = re.match(r"class (\w+)", cls_src)
            if not name_match:
                continue
            cls_name = name_match.group(1)
            was_present = hasattr(site_mod, cls_name)
            try:
                exec(cls_src, site_mod.__dict__)
                injected_count += 1
                if was_present:
                    overwritten.append(cls_name)
            except Exception:
                pass
    if overwritten:
        print(f"[INFO] 覆盖 site-packages 中的类: {overwritten[:10]}", flush=True)
    return injected_count > 0


_inject_vendored_classes()


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="YOLO26 蒸馏性能对比")
    p.add_argument("--teacher", type=str, required=True, help="teacher .pt 路径")
    p.add_argument("--student", type=str, required=True, help="student .pt 路径")
    p.add_argument("--data", type=str, required=True, help="数据集 YAML")
    p.add_argument("--device", type=str, default="0")
    p.add_argument("--imgsz", type=int, default=640)
    p.add_argument("--output", type=str, default="distillation/yolo26/results",
                   help="结果输出目录")
    return p.parse_args()


def count_parameters(model: Any) -> float:
    """参数量（M）"""
    return sum(p.numel() for p in model.parameters()) / 1e6


def get_model_size_mb(weight_path: str) -> float:
    """模型文件大小（MB）"""
    return Path(weight_path).stat().st_size / (1024 * 1024)


def measure_latency(model: Any, imgsz: int, device: str, warmup: int = 10, runs: int = 100) -> float:
    """推理延迟（ms/img）"""
    import torch
    dev = torch.device(device if device != "cpu" else "cpu")
    # 构造随机输入
    dummy = torch.randn(1, 3, imgsz, imgsz, device=dev)
    # warmup
    with torch.no_grad():
        for _ in range(warmup):
            model.model(dummy) if hasattr(model, "model") else model(dummy)
    # 计时
    times = []
    with torch.no_grad():
        for _ in range(runs):
            t0 = time.perf_counter()
            (model.model(dummy) if hasattr(model, "model") else model(dummy))
            if dev.type == "cuda":
                torch.cuda.synchronize()
            times.append((time.perf_counter() - t0) * 1000)
    return float(np.median(times))


def evaluate_map(model_path: str, data: str, imgsz: int, device: str) -> dict[str, float]:
    """用 ultralytics val 评估 mAP"""
    try:
        from ultralytics import YOLO
    except ImportError as e:
        print(f"[ERROR] 无法导入 ultralytics: {e}", file=sys.stderr)
        return {"map50": 0.0, "map50_95": 0.0, "flops_g": 0.0}

    model = YOLO(model_path)
    metrics = model.val(
        data=data,
        imgsz=imgsz,
        device=device,
        verbose=False,
    )

    # ultralytics 的 metrics 对象
    map50 = float(getattr(metrics, "box map50", 0.0)) if hasattr(metrics, "box") else 0.0
    map50_95 = float(getattr(metrics, "box map", 0.0)) if hasattr(metrics, "box") else 0.0

    # FLOPs（如能拿到）
    flops_g = 0.0
    try:
        from ultralytics.utils.torch_utils import get_flops
        flops_g = float(get_flops(model.model, imgsz=imgsz) or 0.0) / 1e9
    except Exception:
        pass

    return {
        "map50": map50,
        "map50_95": map50_95,
        "flops_g": flops_g,
    }


def benchmark_model(
    name: str,
    weight_path: str,
    data: str,
    imgsz: int,
    device: str,
) -> dict[str, Any]:
    """完整 benchmark 单个模型"""
    print(f"\n[INFO] Benchmarking {name}: {weight_path}")
    try:
        from ultralytics import YOLO
    except ImportError as e:
        print(f"[ERROR] {e}", file=sys.stderr)
        return {"name": name, "error": str(e)}

    # 参数量
    model = YOLO(weight_path)
    params_m = count_parameters(model.model)

    # 模型大小
    size_mb = get_model_size_mb(weight_path)

    # mAP
    metrics = evaluate_map(weight_path, data, imgsz, device)

    # 延迟
    latency_ms = measure_latency(model, imgsz, device)

    return {
        "name": name,
        "weight_path": weight_path,
        "params_m": round(params_m, 2),
        "flops_g": round(metrics["flops_g"], 2),
        "map50": round(metrics["map50"], 4),
        "map50_95": round(metrics["map50_95"], 4),
        "latency_ms": round(latency_ms, 2),
        "size_mb": round(size_mb, 2),
    }


def write_csv(rows: list[dict[str, Any]], out_path: Path) -> None:
    if not rows:
        return
    fields = list(rows[0].keys())
    with out_path.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)


def write_markdown(rows: list[dict[str, Any]], out_path: Path) -> None:
    if not rows:
        return
    fields = list(rows[0].keys())
    lines = ["| " + " | ".join(fields) + " |",
             "| " + " | ".join(["---"] * len(fields)) + " |"]
    for r in rows:
        lines.append("| " + " | ".join(str(r.get(f, "")) for f in fields) + " |")
    out_path.write_text("\n".join(lines), encoding="utf-8")


def plot_comparison(rows: list[dict[str, Any]], out_dir: Path) -> None:
    try:
        import matplotlib.pyplot as plt
    except ImportError:
        print("[WARN] matplotlib 未安装，跳过绘图")
        return

    names = [r["name"] for r in rows]
    metrics = ["params_m", "map50", "latency_ms", "size_mb"]
    metric_labels = ["参数量 (M)", "mAP@0.5", "推理延迟 (ms)", "模型大小 (MB)"]

    fig, axes = plt.subplots(2, 2, figsize=(10, 8))
    for ax, m, label in zip(axes.flat, metrics, metric_labels):
        vals = [r.get(m, 0) for r in rows]
        ax.bar(names, vals, color=["#3b82f6", "#10b981"][: len(names)])
        ax.set_title(label)
        ax.set_ylabel(label)
    fig.suptitle("YOLO26 蒸馏性能对比")
    fig.tight_layout()
    fig.savefig(out_dir / "benchmark.png", dpi=120, bbox_inches="tight")
    plt.close(fig)


def main() -> int:
    args = parse_args()
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)

    rows = []
    rows.append(benchmark_model("Teacher (DINO-SO-YOLO)", args.teacher,
                                args.data, args.imgsz, args.device))
    rows.append(benchmark_model("Student (YOLO26n distilled)", args.student,
                                args.data, args.imgsz, args.device))

    # 计算相对变化
    t, s = rows[0], rows[1]
    summary = {
        "params_reduction_pct": round((1 - s["params_m"] / t["params_m"]) * 100, 1) if t["params_m"] else 0,
        "map50_drop_pct": round((1 - s["map50"] / t["map50"]) * 100, 2) if t["map50"] else 0,
        "latency_speedup": round(t["latency_ms"] / s["latency_ms"], 2) if s["latency_ms"] else 0,
        "size_reduction_pct": round((1 - s["size_mb"] / t["size_mb"]) * 100, 1) if t["size_mb"] else 0,
    }

    write_csv(rows, out_dir / "benchmark.csv")
    write_markdown(rows, out_dir / "benchmark.md")
    plot_comparison(rows, out_dir)

    # 打印汇总
    print("\n" + "=" * 60)
    print("蒸馏效果汇总")
    print("=" * 60)
    print(f"参数量减少:    {summary['params_reduction_pct']}%")
    print(f"mAP@0.5 损失:  {summary['map50_drop_pct']}%")
    print(f"推理加速比:    {summary['latency_speedup']}×")
    print(f"模型大小减少:  {summary['size_reduction_pct']}%")
    print(f"\n结果已保存到: {out_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
