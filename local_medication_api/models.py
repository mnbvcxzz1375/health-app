from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class AppBaseModel(BaseModel):
    model_config = ConfigDict(protected_namespaces=())


class DetectionBox(AppBaseModel):
    bbox: list[float]
    confidence: float
    class_id: int
    class_name: str


class ImageInfo(AppBaseModel):
    filename: str
    width: int
    height: int
    content_type: str


class DetectionPayload(AppBaseModel):
    image: ImageInfo
    detections: list[DetectionBox] = Field(default_factory=list)
    inference_time: float
    model_used: str
    raw_bytes: bytes = Field(default=b"", exclude=True, repr=False)


class OCRTextBlock(AppBaseModel):
    text: str
    confidence: float | None = None
    bbox: list[float] | None = None


class OCRPayload(AppBaseModel):
    filename: str
    blocks: list[OCRTextBlock] = Field(default_factory=list)


class MedicationRecognitionItem(AppBaseModel):
    name: str = ""
    alias: str = ""
    dosageValue: int | None = None
    dosageUnit: str = ""
    usage: str = ""
    notes: str = ""
    photoUrl: str = ""
    confidence: float | None = None
    sourceText: str = ""


class MedicationRecognitionResponse(AppBaseModel):
    items: list[MedicationRecognitionItem] = Field(default_factory=list)


class HealthResponse(AppBaseModel):
    status: str
    service: str
    version: str
    model_ready: bool
    weights_path: str
    ocr_provider: str
    scene: str


class PipelineDebugSnapshot(AppBaseModel):
    detections: list[DetectionPayload] = Field(default_factory=list)
    ocrBlocks: list[OCRPayload] = Field(default_factory=list)
    recognizedFields: list[dict[str, Any]] = Field(default_factory=list)
