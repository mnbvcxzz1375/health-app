from __future__ import annotations

import re
from io import BytesIO

import cv2
import numpy as np
from PIL import Image
from rapidocr_onnxruntime import RapidOCR

from local_medication_api.config import settings
from local_medication_api.models import DetectionPayload, OCRPayload, OCRTextBlock


class OCRAdapter:
    """OCR adapter backed by RapidOCR."""

    provider = settings.ocr_provider

    def __init__(self) -> None:
        self._engine: RapidOCR | None = None

    def _get_engine(self) -> RapidOCR:
        if self._engine is None:
            self._engine = RapidOCR()
        return self._engine

    @staticmethod
    def _normalize_text(text: str) -> str:
        return re.sub(r"\s+", " ", text).strip()

    @staticmethod
    def _decode_image(image_bytes: bytes) -> np.ndarray:
        image = cv2.imdecode(np.frombuffer(image_bytes, dtype=np.uint8), cv2.IMREAD_COLOR)
        if image is not None:
            return image
        pil = Image.open(BytesIO(image_bytes)).convert("RGB")
        return cv2.cvtColor(np.array(pil), cv2.COLOR_RGB2BGR)

    def _run_ocr(self, image: np.ndarray) -> list[OCRTextBlock]:
        if not settings.ocr_enabled:
            return []

        engine = self._get_engine()
        result, _elapsed = engine(image)
        blocks: list[OCRTextBlock] = []
        for item in result or []:
            if len(item) < 3:
                continue
            points, text, confidence = item
            normalized = self._normalize_text(str(text))
            confidence_value = float(confidence) if confidence is not None else None
            if not normalized:
                continue
            if confidence_value is not None and confidence_value < settings.ocr_min_confidence:
                continue
            bbox = None
            if points:
                xs = [float(point[0]) for point in points]
                ys = [float(point[1]) for point in points]
                bbox = [min(xs), min(ys), max(xs), max(ys)]
            blocks.append(
                OCRTextBlock(
                    text=normalized,
                    confidence=confidence_value,
                    bbox=bbox,
                )
            )
        return blocks

    @staticmethod
    def _crop_regions(image: np.ndarray, payload: DetectionPayload) -> list[np.ndarray]:
        crops: list[np.ndarray] = []
        height, width = image.shape[:2]
        ordered = sorted(payload.detections, key=lambda item: item.confidence, reverse=True)
        for detection in ordered[: settings.ocr_max_crops]:
            x1, y1, x2, y2 = detection.bbox
            pad_x = max(8, int((x2 - x1) * 0.08))
            pad_y = max(8, int((y2 - y1) * 0.08))
            left = max(0, int(x1) - pad_x)
            top = max(0, int(y1) - pad_y)
            right = min(width, int(x2) + pad_x)
            bottom = min(height, int(y2) + pad_y)
            if right - left < 24 or bottom - top < 24:
                continue
            crops.append(image[top:bottom, left:right])
        return crops

    @staticmethod
    def _merge_blocks(full_blocks: list[OCRTextBlock], crop_blocks: list[OCRTextBlock]) -> list[OCRTextBlock]:
        merged: dict[str, OCRTextBlock] = {}
        for block in [*full_blocks, *crop_blocks]:
            previous = merged.get(block.text)
            if previous is None or (block.confidence or 0.0) > (previous.confidence or 0.0):
                merged[block.text] = block
        return sorted(
            merged.values(),
            key=lambda item: (item.confidence or 0.0, len(item.text)),
            reverse=True,
        )

    def extract(self, detection_payloads: list[DetectionPayload]) -> list[OCRPayload]:
        payloads: list[OCRPayload] = []
        for payload in detection_payloads:
            image = self._decode_image(payload.raw_bytes)
            full_blocks = self._run_ocr(image)
            crop_blocks: list[OCRTextBlock] = []
            for crop in self._crop_regions(image, payload):
                crop_blocks.extend(self._run_ocr(crop))
            payloads.append(
                OCRPayload(
                    filename=payload.image.filename,
                    blocks=self._merge_blocks(full_blocks, crop_blocks),
                )
            )
        return payloads


ocr_adapter = OCRAdapter()
