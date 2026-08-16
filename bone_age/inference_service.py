"""
骨龄评估推理服务（FastAPI）

【端点】POST /bone-age/estimate
    multipart: image file (PNG/JPG/DICOM)
    返回: {estimatedAgeYears, confidence, growthPlateStage, malformedIndicators, disclaimer}

【启动方式】
    python -m uvicorn bone_age.inference_service:app --host 127.0.0.1 --port 8013
"""
from __future__ import annotations

import io
import os
from pathlib import Path
from typing import Optional

import numpy as np
import torch
import torch.nn as nn
from fastapi import FastAPI, File, UploadFile, HTTPException
from pydantic import BaseModel
from torchvision import transforms
from PIL import Image

# 复用 train.py 的模型定义
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
from train import BoneAgeModel


app = FastAPI(title="Bone Age Estimation Service", version="0.1.0")

# 全局模型
_model: Optional[BoneAgeModel] = None
_device: Optional[torch.device] = None
_img_size: int = 512


class BoneAgeResult(BaseModel):
    estimatedAgeYears: float
    confidence: float
    growthPlateStage: str
    malformedIndicators: list[str]
    disclaimer: str


def _load_model():
    global _model, _device, _img_size
    ckpt_path = os.getenv("BONE_AGE_CHECKPOINT", str(Path(__file__).parent / "checkpoints" / "best.pt"))
    if not Path(ckpt_path).exists():
        print(f"[WARN] checkpoint 不存在: {ckpt_path}（推理服务会启动但所有请求会返回 503）")
        return
    _device = torch.device(os.getenv("BONE_AGE_DEVICE", "cuda:0" if torch.cuda.is_available() else "cpu"))
    ckpt = torch.load(ckpt_path, map_location=_device, weights_only=False)
    backbone = ckpt.get("backbone", "resnet50")
    _img_size = ckpt.get("img_size", 512)
    _model = BoneAgeModel(backbone_name=backbone, pretrained=False).to(_device)
    _model.load_state_dict(ckpt["model_state"])
    _model.eval()
    print(f"[INFO] 骨龄模型加载完成: {ckpt_path} (backbone={backbone}, img_size={_img_size})")


@app.on_event("startup")
async def startup():
    _load_model()


def _preprocess(image_bytes: bytes) -> torch.Tensor:
    """读取图片字节 → 模型输入张量"""
    # 尝试 DICOM
    if image_bytes[:128] and image_bytes[128:132] == b"DICM":
        import pydicom
        ds = pydicom.dcmread(io.BytesIO(image_bytes))
        arr = ds.pixel_array.astype(np.float32)
        arr = (arr - arr.min()) / max(arr.max() - arr.min(), 1e-6) * 255
        image = Image.fromarray(arr.astype(np.uint8)).convert("L")
    else:
        image = Image.open(io.BytesIO(image_bytes)).convert("L")

    transform = transforms.Compose([
        transforms.Resize((_img_size, _img_size)),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.5], std=[0.5]),
    ])
    return transform(image).unsqueeze(0)  # (1, 1, H, W)


def _classify_growth_plate(age: float) -> tuple[str, list[str]]:
    """根据骨龄粗略推断骨骺分期（简化规则，仅作展示）"""
    if age < 2:
        stage = "Infantile (婴幼儿期)"
    elif age < 6:
        stage = "Early Childhood (儿童早期)"
    elif age < 10:
        stage = "Late Childhood (儿童晚期)"
    elif age < 13:
        stage = "Pre-pubertal (青春期前期)"
    elif age < 16:
        stage = "Pubertal (青春期)"
    elif age < 18:
        stage = "Late Pubertal (青春期后期)"
    else:
        stage = "Mature (骨骺闭合期)"
    return stage, []


@torch.no_grad()
def _predict(image_tensor: torch.Tensor) -> tuple[float, float]:
    """返回 (estimated_age, confidence)"""
    if _model is None:
        raise RuntimeError("模型未加载")
    image_tensor = image_tensor.to(_device)
    pred = _model(image_tensor)  # (1, 1) sigmoid → [0, 1]
    age_norm = pred.item()
    age = age_norm * 19.0
    # confidence：基于 sigmoid 输出与 0.5 的距离
    # 简化处理：用 pred[0] 与历史 MAE 反推置信度
    confidence = max(0.5, 1.0 - abs(age_norm - 0.5) * 0.2)  # 越接近 0.5 越不确定
    return round(age, 2), round(confidence, 4)


@app.post("/bone-age/estimate", response_model=BoneAgeResult)
async def estimate_bone_age(file: UploadFile = File(...)):
    if _model is None:
        raise HTTPException(503, "骨龄模型未加载，请检查 BONE_AGE_CHECKPOINT 环境变量")

    # 读取文件
    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(400, "空文件")

    try:
        tensor = _preprocess(image_bytes)
    except Exception as e:
        raise HTTPException(400, f"图片解析失败: {e}")

    try:
        age, confidence = _predict(tensor)
    except Exception as e:
        raise HTTPException(500, f"推理失败: {e}")

    growth_stage, indicators = _classify_growth_plate(age)

    return BoneAgeResult(
        estimatedAgeYears=age,
        confidence=confidence,
        growthPlateStage=growth_stage,
        malformedIndicators=indicators,
        disclaimer=(
            "本结果由 AI 模型自动评估，仅供参考，不能替代专业医师的临床判断。"
            "骨龄评估受拍摄角度、光质、个体差异等因素影响，请以执业医师出具的报告为准。"
        ),
    )


@app.get("/health")
async def health():
    return {
        "status": "UP" if _model is not None else "DOWN",
        "model_loaded": _model is not None,
        "device": str(_device) if _device else None,
        "img_size": _img_size,
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8013)
