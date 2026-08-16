from __future__ import annotations

import os
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[1]
API_DIR = ROOT_DIR / "local_medication_api"
DEFAULT_WEIGHTS_PATH = API_DIR / "weights" / "yolov13n.pt"
# 蒸馏后的 YOLO26 权重（由 distillation/yolo26/ 产出）
DEFAULT_DISTILLED_WEIGHTS_PATH = API_DIR / "weights" / "yolo26n_distilled.pt"


class Settings:
    app_name = "health-app Local Medication API"
    app_version = "0.1.0"
    scene_name = "medication_recognition"
    # 切换到蒸馏后 YOLO26 权重（端到端无 NMS，更快更轻）
    use_distilled = os.getenv("LOCAL_MEDICATION_USE_DISTILLED", "false").lower() == "true"
    # 用户显式指定权重路径时优先；否则按 use_distilled 自动选择
    _explicit_weights = os.getenv("LOCAL_MEDICATION_WEIGHTS")
    weights_path = (
        Path(_explicit_weights).expanduser() if _explicit_weights
        else (DEFAULT_DISTILLED_WEIGHTS_PATH if use_distilled else DEFAULT_WEIGHTS_PATH)
    )
    device = os.getenv("LOCAL_MEDICATION_DEVICE", "cpu")
    confidence = float(os.getenv("LOCAL_MEDICATION_CONFIDENCE", "0.25"))
    # YOLO26 端到端无 NMS，iou 参数失效但保留以兼容旧调用
    iou = float(os.getenv("LOCAL_MEDICATION_IOU", "0.45"))
    image_size = int(os.getenv("LOCAL_MEDICATION_IMAGE_SIZE", "640"))
    max_det = int(os.getenv("LOCAL_MEDICATION_MAX_DET", "25"))
    host = os.getenv("LOCAL_MEDICATION_HOST", "127.0.0.1")
    port = int(os.getenv("LOCAL_MEDICATION_PORT", "8011"))
    ocr_enabled = os.getenv("LOCAL_MEDICATION_OCR_ENABLED", "true").lower() == "true"
    ocr_provider = os.getenv("LOCAL_MEDICATION_OCR_PROVIDER", "rapidocr_onnxruntime")
    ocr_min_confidence = float(os.getenv("LOCAL_MEDICATION_OCR_MIN_CONFIDENCE", "0.45"))
    ocr_max_crops = int(os.getenv("LOCAL_MEDICATION_OCR_MAX_CROPS", "4"))


settings = Settings()
