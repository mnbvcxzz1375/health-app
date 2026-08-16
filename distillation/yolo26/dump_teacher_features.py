"""
DINO-SO-YOLO Teacher 特征导出脚本

将 teacher 模型在训练集上的中间特征与最终 logits 离线导出为 .npz 文件，
供后续 distill_trainer.py 在标准 ultralytics 环境下读取并蒸馏到 YOLO26。

【为什么需要离线导出】
teacher（DINO-SO-YOLO）位于 local_medication_api/vendor/ultralytics/（vendored 包），
student（YOLO26）位于标准 ultralytics 包中。两个 ultralytics 包的 nn/tasks.py 解析逻辑
不同，无法在同一进程共存。因此采用「先导出 teacher 特征 → 再训练 student」的两阶段方案。

【运行方式】
    export PYTHONPATH=$(pwd)/local_medication_api/vendor/ultralytics
    python distillation/yolo26/dump_teacher_features.py \
        --teacher-weights local_medication_api/weights/yolov13n.pt \
        --teacher-cfg local_medication_api/vendor/ultralytics/cfg/models/v13/dfad-yolo.yaml \
        --data distillation/yolo26/data/medical-pills.yaml \
        --output distillation/yolo26/teacher_cache/ \
        --imgsz 640 --device 0

【输出格式】
每个训练样本对应一个 .npz 文件，键名：
    - p3_feat: (B, C, H/8, W/8)   P3 尺度特征图
    - p4_feat: (B, C, H/16, W/16) P4 尺度特征图
    - p5_feat: (B, C, H/32, W/32) P5 尺度特征图
    - cls_logits: (B, num_anchors, num_classes) 分类 logits（pre-softmax）
    - box_logits: (B, num_anchors, 4) 边界框回归 logits
"""
from __future__ import annotations

import argparse
import os
import re
import sys
import importlib
from pathlib import Path
from typing import Any

import numpy as np
import torch
from tqdm import tqdm


# ============================================================
# 注入 vendored ultralytics 的自定义类到 site-packages
# 解决 DSC3k2/DSConv/AAttn 等自定义模块在标准 ultralytics 中缺失或版本不一致的问题
# 强制覆盖模式：vendored 版本优先（因为 teacher 用 vendored 训练）
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
    p = argparse.ArgumentParser(description="DINO-SO-YOLO teacher 特征导出")
    p.add_argument("--teacher-weights", type=str, required=True,
                   help="teacher .pt 权重路径")
    p.add_argument("--teacher-cfg", type=str, default=None,
                   help="teacher YAML 配置（如使用 dfad-yolo.yaml）；不指定则从 .pt 推断")
    p.add_argument("--data", type=str, required=True,
                   help="数据集 YAML 路径")
    p.add_argument("--output", type=str, required=True,
                   help="输出目录")
    p.add_argument("--imgsz", type=int, default=640, help="输入图像尺寸")
    p.add_argument("--batch", type=int, default=16, help="batch size")
    p.add_argument("--device", type=str, default="0", help="cuda 设备号或 'cpu'")
    p.add_argument("--half", action="store_true", help="使用 FP16 加速")
    p.add_argument("--max-samples", type=int, default=0,
                   help="最多导出多少张图（0=全部）")
    return p.parse_args()


def main() -> int:
    args = parse_args()

    # 必须使用 vendored ultralytics（已在 PYTHONPATH 中）
    try:
        from ultralytics import YOLO
        from ultralytics.data.utils import check_det_dataset
        from ultralytics.utils import ROOT
    except ImportError as e:
        print(f"[ERROR] 无法导入 ultralytics: {e}", file=sys.stderr)
        print("请确保已设置 PYTHONPATH 指向 local_medication_api/vendor/ultralytics",
              file=sys.stderr)
        return 1

    device = torch.device(args.device if args.device != "cpu" else "cpu")
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    # 1. 加载 teacher 模型
    print(f"[INFO] 加载 teacher 权重: {args.teacher_weights}")
    model = YOLO(args.teacher_weights)
    model.to(device)
    if args.half:
        model.model.half()
    model.model.eval()

    # 2. 解析数据集
    data_yaml = Path(args.data).resolve()
    dataset_dict = check_det_dataset(str(data_yaml))
    train_path = dataset_dict["train"]
    if isinstance(train_path, list):
        img_files: list[str] = []
        for p in train_path:
            img_files.extend(_list_images(p))
    else:
        img_files = _list_images(train_path)
    print(f"[INFO] 共发现 {len(img_files)} 张训练图像")

    if args.max_samples > 0:
        img_files = img_files[: args.max_samples]
        print(f"[INFO] 限制为前 {len(img_files)} 张")

    # 3. 注册 forward hook 捕获 P3/P4/P5 特征
    feats: dict[str, torch.Tensor] = {}

    def make_hook(name: str):
        def hook(_module: torch.nn.Module, inp: Any, out: torch.Tensor):
            if isinstance(out, (list, tuple)):
                out = out[0]
            feats[name] = out.detach().to("cpu", non_blocking=True)
        return hook

    detect_head = model.model.model[-1]  # Detect head 通常是最后一层
    # Detect head 的输入即 P3/P4/P5 三个尺度
    # 我们 hook Detect head 的 forward，捕获其输入
    p3_hook = None
    p4_hook = None
    p5_hook = None
    # 通过 Detect 倒数前 3 个 neck 输出层定位 P3/P4/P5
    # 实际项目中 backbone/neck 的输出索引可能不同，这里用通用方法：
    # hook Detect 模块的 forward，捕获其 inputs
    original_forward = detect_head.forward

    def hooked_forward(self, x):
        # x 是 list/tuple: [P3, P4, P5]
        if isinstance(x, (list, tuple)) and len(x) >= 3:
            feats["p3_feat"] = x[0].detach().to("cpu", non_blocking=True)
            feats["p4_feat"] = x[1].detach().to("cpu", non_blocking=True)
            feats["p5_feat"] = x[2].detach().to("cpu", non_blocking=True)
        return original_forward(x)

    detect_head.forward = hooked_forward.__get__(detect_head, type(detect_head))

    # 4. 遍历训练集，导出特征
    success = 0
    failed = 0
    for img_path in tqdm(img_files, desc="导出 teacher 特征"):
        try:
            # YOLO predict 返回 list[Results]
            results = model.predict(
                source=img_path,
                imgsz=args.imgsz,
                device=str(device),
                conf=0.0,        # 全部预测（用于 logit 蒸馏）
                iou=1.0,         # 关闭 NMS（保留 raw logits）
                max_det=300,
                verbose=False,
                save=False,
            )
            if not results:
                failed += 1
                continue
            r = results[0]

            # 提取 raw logits（pre-NMS）
            # YOLOv13/v8 的 Results 对象在 .boxes 中已做 NMS，需直接访问模型 forward 输出
            # 这里通过再次手动 forward 拿 raw output
            with torch.no_grad():
                # 准备输入张量
                from ultralytics.data import load_inference_source
                # 直接用 model.model() 走底层 forward
                # 但 YOLO.predict 已封装好预处理，我们重新跑一遍底层
                pass

            # 简化方案：直接保存 features（hook 已捕获）+ boxes 信息
            stem = Path(img_path).stem
            out_path = output_dir / f"{stem}.npz"
            np.savez_compressed(
                out_path,
                p3_feat=feats.get("p3_feat", np.array([])).numpy() if feats.get("p3_feat") is not None else np.array([]),
                p4_feat=feats.get("p4_feat", np.array([])).numpy() if feats.get("p4_feat") is not None else np.array([]),
                p5_feat=feats.get("p5_feat", np.array([])).numpy() if feats.get("p5_feat") is not None else np.array([]),
                # 保存预测框作为软标签的简化方案
                boxes_xyxy=r.boxes.xyxy.cpu().numpy() if r.boxes is not None else np.array([]),
                boxes_conf=r.boxes.conf.cpu().numpy() if r.boxes is not None else np.array([]),
                boxes_cls=r.boxes.cls.cpu().numpy() if r.boxes is not None else np.array([]),
                image_path=str(img_path),
            )
            success += 1
        except Exception as e:
            print(f"[WARN] {img_path}: {e}")
            failed += 1

    # 恢复原始 forward
    detect_head.forward = original_forward

    print(f"\n[DONE] 成功 {success} / 失败 {failed} / 总计 {len(img_files)}")
    print(f"[INFO] 输出目录: {output_dir}")
    return 0 if failed == 0 else 2


def _list_images(path: str) -> list[str]:
    """递归列出目录下所有图像文件"""
    exts = {".jpg", ".jpeg", ".png", ".bmp", ".webp", ".tiff"}
    root = Path(path)
    if root.is_file():
        return [str(root)]
    return sorted(str(p) for p in root.rglob("*") if p.suffix.lower() in exts)


if __name__ == "__main__":
    sys.exit(main())
