from __future__ import annotations

from io import BytesIO
from pathlib import Path
import sys
from threading import Lock
from time import perf_counter

from PIL import Image

from local_medication_api.config import settings
from local_medication_api.models import DetectionBox, DetectionPayload, ImageInfo

# 根据是否使用蒸馏权重选择 ultralytics 包
# - use_distilled=True: 标准 ultralytics（含 YOLO26，端到端无 NMS）
# - use_distilled=False: vendored ultralytics（DINO-SO-YOLO，需要 vendor 目录）
VENDOR_DIR = Path(__file__).resolve().parents[1] / "vendor"

if settings.use_distilled:
    # 蒸馏后 YOLO26 权重需标准 ultralytics 包
    # 确保不优先使用 vendored 版本
    sys.path[:] = [p for p in sys.path if p and Path(p).resolve() != VENDOR_DIR.resolve()]
else:
    if str(VENDOR_DIR) not in sys.path:
        sys.path.insert(0, str(VENDOR_DIR))

from ultralytics import YOLO


class MedicationDetector:
    def __init__(self) -> None:
        self._model: YOLO | None = None
        self._lock = Lock()

    def is_ready(self) -> bool:
        return settings.weights_path.exists()

    def model_name(self) -> str:
        return settings.weights_path.name

    def _load_model(self) -> YOLO:
        if self._model is None:
            with self._lock:
                if self._model is None:
                    if not settings.weights_path.exists():
                        raise FileNotFoundError(f"Model weights not found: {settings.weights_path}")
                    self._model = YOLO(str(settings.weights_path))
        return self._model

    def detect(self, image_bytes: bytes, filename: str, content_type: str) -> DetectionPayload:
        image = Image.open(BytesIO(image_bytes)).convert("RGB")
        width, height = image.size
        model = self._load_model()
        started = perf_counter()
        # YOLO26 端到端无 NMS，iou 参数对结果无影响（保留以兼容配置）
        # YOLOv13/DINO-SO-YOLO 走标准 NMS 流程
        results = model.predict(
            source=image,
            conf=settings.confidence,
            iou=settings.iou,
            imgsz=settings.image_size,
            max_det=settings.max_det,
            device=settings.device,
            verbose=False,
        )
        duration = perf_counter() - started

        detections: list[DetectionBox] = []
        if results:
            result = results[0]
            if result.boxes is not None:
                for box in result.boxes:
                    cls_id = int(box.cls[0].item())
                    detections.append(
                        DetectionBox(
                            bbox=[round(value, 2) for value in box.xyxy[0].tolist()],
                            confidence=round(float(box.conf[0].item()), 4),
                            class_id=cls_id,
                            class_name=str(result.names.get(cls_id, f"class_{cls_id}")),
                        )
                    )

        return DetectionPayload(
            image=ImageInfo(
                filename=filename,
                width=width,
                height=height,
                content_type=content_type,
            ),
            detections=detections,
            inference_time=round(duration, 4),
            model_used=self.model_name(),
            raw_bytes=image_bytes,
        )


detector = MedicationDetector()
