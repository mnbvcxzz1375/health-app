"""
骨龄评估模型训练

【数据集】RSNA Pediatric Bone Age（12600 张左手腕 X 光，0-19 岁）
  - HuggingFace: raphaelvallat/pediatric_bone_age
  - Kaggle: kmader/rsna-bone-age

【模型】ResNet50 backbone + 回归头（输出 1 个 float：骨龄岁数）
  - 输入：512×512 灰度图（X 光）
  - 损失：MSE + L1（混合）

【运行方式】
    python bone_age/train.py \
        --data-dir /data/rsna-bone-age/ \
        --output bone_age/checkpoints/ \
        --epochs 50 --batch 32 --lr 1e-4 --device cuda:0

【数据目录结构（期望）】
    /data/rsna-bone-age/
    ├── train/
    │   ├── 1377.png
    │   ├── 1378.png
    │   └── ...
    ├── train.csv  # columns: id, boneage
    ├── val/
    └── val.csv    # columns: id, boneage
"""
from __future__ import annotations

import argparse
import csv
import json
import os
from pathlib import Path
from typing import Optional

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import Dataset, DataLoader
from torch.optim import AdamW
from torch.optim.lr_scheduler import CosineAnnealingLR
from torchvision import transforms
from PIL import Image


# ============================================================
# 1. Dataset
# ============================================================

class BoneAgeDataset(Dataset):
    """RSNA Bone Age dataset"""

    def __init__(
        self,
        csv_path: Path,
        images_dir: Path,
        transform=None,
        is_dicom: bool = False,
    ):
        self.samples: list[tuple[Path, float]] = []
        with csv_path.open("r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                img_id = row.get("id") or row.get("Image ID") or row.get("image_id")
                boneage = float(row.get("boneage") or row.get("Bone Age") or row.get("age"))
                if img_id is None or boneage is None:
                    continue
                # 尝试 .png / .jpg / .dcm
                for ext in (".png", ".jpg", ".jpeg", ".dcm"):
                    p = images_dir / f"{img_id}{ext}"
                    if p.exists():
                        self.samples.append((p, boneage))
                        break
        self.transform = transform
        self.is_dicom = is_dicom

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        img_path, boneage = self.samples[idx]
        if img_path.suffix.lower() == ".dcm":
            import pydicom
            ds = pydicom.dcmread(str(img_path))
            img_array = ds.pixel_array.astype(np.float32)
            # 归一化到 0-255
            img_array = (img_array - img_array.min()) / max(img_array.max() - img_array.min(), 1e-6) * 255
            image = Image.fromarray(img_array.astype(np.uint8)).convert("L")
        else:
            image = Image.open(img_path).convert("L")

        if self.transform:
            image = self.transform(image)

        # 骨龄归一化到 [0, 1]（0-19 岁 → 0-1）
        boneage_norm = torch.tensor([boneage / 19.0], dtype=torch.float32)
        return image, boneage_norm, torch.tensor([boneage], dtype=torch.float32)


def get_transforms(is_train: bool = True, img_size: int = 512):
    """数据增强：随机裁剪、旋转、对比度"""
    if is_train:
        return transforms.Compose([
            transforms.Resize((img_size + 32, img_size + 32)),
            transforms.RandomCrop((img_size, img_size)),
            transforms.RandomRotation(degrees=10),
            transforms.ColorJitter(brightness=0.2, contrast=0.2),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.5], std=[0.5]),  # 灰度图
        ])
    else:
        return transforms.Compose([
            transforms.Resize((img_size, img_size)),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.5], std=[0.5]),
        ])


# ============================================================
# 2. Model: ResNet50 + 回归头
# ============================================================

class BoneAgeModel(nn.Module):
    """ResNet50 backbone + 回归头（输出 1 个 float，归一化骨龄）"""

    def __init__(self, backbone_name: str = "resnet50", pretrained: bool = True):
        super().__init__()
        try:
            import timm
            # timm 的 resnet50 默认输入 3 通道，需改为单通道
            self.backbone = timm.create_model(
                backbone_name,
                pretrained=pretrained,
                in_chans=1,
                num_classes=0,  # 移除分类头
            )
        except ImportError:
            from torchvision.models import resnet50, ResNet50_Weights
            weights = ResNet50_Weights.IMAGENET1K_V2 if pretrained else None
            self.backbone = resnet50(weights=weights)
            # 改第一层为单通道
            old_conv = self.backbone.conv1
            self.backbone.conv1 = nn.Conv2d(
                1, old_conv.out_channels,
                kernel_size=old_conv.kernel_size, stride=old_conv.stride,
                padding=old_conv.padding, bias=False
            )
            # 移除分类头
            self.backbone.fc = nn.Identity()

        # 回归头
        feat_dim = self.backbone.num_features if hasattr(self.backbone, "num_features") else 2048
        self.regression_head = nn.Sequential(
            nn.Linear(feat_dim, 512),
            nn.ReLU(),
            nn.Dropout(0.3),
            nn.Linear(512, 1),
            nn.Sigmoid(),  # 输出 [0, 1]（对应 0-19 岁）
        )

    def forward(self, x):
        feat = self.backbone(x)
        return self.regression_head(feat)


# ============================================================
# 3. 训练 / 评估
# ============================================================

def train_one_epoch(model, loader, optimizer, scheduler, device, epoch, total_epochs):
    model.train()
    total_loss = 0.0
    n = 0
    for batch_idx, (images, boneage_norm, boneage_raw) in enumerate(loader):
        images = images.to(device)
        boneage_norm = boneage_norm.to(device)

        optimizer.zero_grad()
        pred = model(images)  # (B, 1)
        # 混合损失：MSE + L1
        loss_mse = F.mse_loss(pred, boneage_norm)
        loss_l1 = F.l1_loss(pred, boneage_norm)
        loss = loss_mse + 0.5 * loss_l1
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()

        total_loss += loss.item() * images.size(0)
        n += images.size(0)

        if (batch_idx + 1) % 20 == 0:
            print(f"[Train] epoch {epoch+1}/{total_epochs} batch {batch_idx+1}/{len(loader)} "
                  f"loss={loss.item():.4f} mse={loss_mse.item():.4f} l1={loss_l1.item():.4f}", flush=True)

    scheduler.step()
    return total_loss / max(n, 1)


@torch.no_grad()
def evaluate(model, loader, device):
    model.eval()
    total_loss = 0.0
    total_mae = 0.0  # 平均绝对误差（岁数）
    n = 0
    for images, boneage_norm, boneage_raw in loader:
        images = images.to(device)
        boneage_norm = boneage_norm.to(device)
        pred = model(images)
        loss = F.mse_loss(pred, boneage_norm)
        total_loss += loss.item() * images.size(0)
        # MAE on 原始岁数
        pred_age = (pred * 19.0).cpu()
        total_mae += (pred_age - boneage_raw).abs().sum().item()
        n += images.size(0)
    return total_loss / max(n, 1), total_mae / max(n, 1)


# ============================================================
# 4. 主流程
# ============================================================

def parse_args():
    p = argparse.ArgumentParser(description="骨龄评估模型训练")
    p.add_argument("--data-dir", type=str, required=True,
                   help="数据集根目录（含 train/ val/ train.csv val.csv）")
    p.add_argument("--output", type=str, default="bone_age/checkpoints/")
    p.add_argument("--backbone", type=str, default="resnet50")
    p.add_argument("--pretrained", action="store_true", default=True)
    p.add_argument("--img-size", type=int, default=512)
    p.add_argument("--epochs", type=int, default=50)
    p.add_argument("--batch", type=int, default=32)
    p.add_argument("--lr", type=float, default=1e-4)
    p.add_argument("--weight-decay", type=float, default=1e-4)
    p.add_argument("--workers", type=int, default=8)
    p.add_argument("--device", type=str, default="cuda:0")
    return p.parse_args()


def main() -> int:
    args = parse_args()
    device = torch.device(args.device)
    data_dir = Path(args.data_dir)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    # 数据
    train_csv = data_dir / "train.csv"
    val_csv = data_dir / "val.csv"
    train_dir = data_dir / "train"
    val_dir = data_dir / "val"

    if not train_csv.exists() or not val_csv.exists():
        print(f"[ERROR] 缺少 train.csv 或 val.csv，请检查 --data-dir")
        print(f"  期望路径: {train_csv}, {val_csv}")
        return 1

    print(f"[INFO] 加载训练集: {train_csv}")
    train_ds = BoneAgeDataset(train_csv, train_dir, get_transforms(is_train=True, img_size=args.img_size))
    val_ds = BoneAgeDataset(val_csv, val_dir, get_transforms(is_train=False, img_size=args.img_size))
    print(f"[INFO] train={len(train_ds)} val={len(val_ds)}")

    train_loader = DataLoader(
        train_ds, batch_size=args.batch, shuffle=True,
        num_workers=args.workers, pin_memory=True, drop_last=True,
    )
    val_loader = DataLoader(
        val_ds, batch_size=args.batch, shuffle=False,
        num_workers=args.workers, pin_memory=True,
    )

    # 模型
    print(f"[INFO] 构建 {args.backbone} 模型")
    model = BoneAgeModel(backbone_name=args.backbone, pretrained=args.pretrained).to(device)
    n_params = sum(p.numel() for p in model.parameters()) / 1e6
    print(f"[INFO] 参数量: {n_params:.2f}M")

    optimizer = AdamW(model.parameters(), lr=args.lr, weight_decay=args.weight_decay)
    scheduler = CosineAnnealingLR(optimizer, T_max=args.epochs)

    best_mae = float("inf")
    history = []

    for epoch in range(args.epochs):
        train_loss = train_one_epoch(model, train_loader, optimizer, scheduler, device, epoch, args.epochs)
        val_loss, val_mae = evaluate(model, val_loader, device)
        print(f"[Epoch {epoch+1}/{args.epochs}] train_loss={train_loss:.4f} val_loss={val_loss:.4f} val_mae={val_mae:.4f} 岁",
              flush=True)
        history.append({
            "epoch": epoch + 1,
            "train_loss": round(train_loss, 4),
            "val_loss": round(val_loss, 4),
            "val_mae_years": round(val_mae, 4),
            "lr": optimizer.param_groups[0]["lr"],
        })

        # 保存 best
        if val_mae < best_mae:
            best_mae = val_mae
            ckpt = {
                "epoch": epoch + 1,
                "model_state": model.state_dict(),
                "optimizer_state": optimizer.state_dict(),
                "scheduler_state": scheduler.state_dict(),
                "val_mae_years": val_mae,
                "backbone": args.backbone,
                "img_size": args.img_size,
            }
            torch.save(ckpt, output_dir / "best.pt")
            print(f"[INFO] best checkpoint saved: val_mae={val_mae:.4f} 岁", flush=True)

    # 保存 last
    torch.save({
        "epoch": args.epochs,
        "model_state": model.state_dict(),
        "val_mae_years": val_mae,
        "backbone": args.backbone,
        "img_size": args.img_size,
    }, output_dir / "last.pt")

    # 保存训练历史
    with (output_dir / "history.json").open("w", encoding="utf-8") as f:
        json.dump({"best_mae_years": best_mae, "epochs": history}, f, indent=2, ensure_ascii=False)

    print(f"\n[DONE] best_val_mae = {best_mae:.4f} 岁")
    print(f"[DONE] checkpoints: {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
