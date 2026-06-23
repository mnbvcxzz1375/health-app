from __future__ import annotations

import re

from local_medication_api.models import (
    DetectionPayload,
    MedicationRecognitionItem,
    MedicationRecognitionResponse,
    OCRPayload,
    PipelineDebugSnapshot,
)


MEDICATION_NAME_KEYWORDS = (
    "片",
    "胶囊",
    "颗粒",
    "口服液",
    "滴眼液",
    "滴剂",
    "喷雾",
    "丸",
    "散",
    "膏",
    "贴",
    "药",
)
LICENSE_PATTERNS = (
    "国药准字",
    "批准文号",
)
MANUFACTURER_KEYWORDS = (
    "制药",
    "药业",
    "有限公司",
    "股份",
    "公司",
)
USAGE_OPTIONS = ("饭前", "饭后", "随餐", "睡前", "按需")
DOSAGE_PATTERNS = [
    re.compile(r"(?:每次|一次)(\d+)\s*(片|粒|袋|支|丸|滴|毫升|ml|mL|胶囊|颗)"),
    re.compile(r"(\d+)\s*(片|粒|袋|支|丸|滴|毫升|ml|mL|胶囊|颗)\s*/\s*次"),
]
UNIT_FALLBACK_PATTERN = re.compile(r"(\d+)\s*(片|粒|袋|支|丸|滴|毫升|ml|mL|胶囊|颗)")


def _collect_texts(ocr_payload: OCRPayload) -> list[str]:
    return [block.text.strip() for block in ocr_payload.blocks if block.text.strip()]


def _extract_name(texts: list[str]) -> str:
    candidates: list[str] = []
    for text in texts:
        if len(text) > 40:
            continue
        if any(marker in text for marker in LICENSE_PATTERNS):
            continue
        if any(marker in text for marker in MANUFACTURER_KEYWORDS):
            continue
        if re.search(r"[A-Za-z]{3,}", text):
            continue
        if any(keyword in text for keyword in MEDICATION_NAME_KEYWORDS):
            candidates.append(text)
    if not candidates:
        return ""
    candidates.sort(key=lambda item: (sum("\u4e00" <= ch <= "\u9fff" for ch in item), len(item)), reverse=True)
    return candidates[0]


def _extract_usage(texts: list[str]) -> str:
    merged = " ".join(texts)
    for option in USAGE_OPTIONS:
        if option in merged:
            return option
    return ""


def _extract_dosage(texts: list[str]) -> tuple[int | None, str]:
    merged = " ".join(texts)
    for pattern in DOSAGE_PATTERNS:
        match = pattern.search(merged)
        if match:
            return int(match.group(1)), match.group(2).replace("mL", "ml")
    unit_match = UNIT_FALLBACK_PATTERN.search(merged)
    if unit_match:
        return None, unit_match.group(2).replace("mL", "ml")
    return None, ""


def _extract_notes(texts: list[str], chosen_name: str) -> str:
    notes: list[str] = []
    for text in texts:
        if text == chosen_name or text in notes:
            continue
        if len(text) <= 1:
            continue
        notes.append(text)
        if len(notes) >= 4:
            break
    return "；".join(notes)


def _build_source_text(payload: DetectionPayload, ocr_payload: OCRPayload) -> str:
    if ocr_payload.blocks:
        return " ".join(block.text for block in ocr_payload.blocks[:8] if block.text)

    if payload.detections:
        summary = ", ".join(
            f"{item.class_name}({item.confidence:.2f})" for item in payload.detections[:3]
        )
        return f"检测到 {len(payload.detections)} 个候选区域：{summary}"

    return "未检测到明确药品候选区域，OCR 尚未识别出有效文本。"


def build_response(
    detection_payloads: list[DetectionPayload],
    ocr_payloads: list[OCRPayload],
) -> tuple[MedicationRecognitionResponse, PipelineDebugSnapshot]:
    items: list[MedicationRecognitionItem] = []
    recognized_fields: list[dict[str, object]] = []
    ocr_by_filename = {payload.filename: payload for payload in ocr_payloads}

    for detection in detection_payloads:
        ocr_payload = ocr_by_filename.get(detection.image.filename, OCRPayload(filename=detection.image.filename))
        texts = _collect_texts(ocr_payload)
        name = _extract_name(texts)
        dosage_value, dosage_unit = _extract_dosage(texts)
        usage = _extract_usage(texts)
        item = MedicationRecognitionItem(
            name=name,
            alias="",
            dosageValue=dosage_value,
            dosageUnit=dosage_unit,
            usage=usage,
            notes=_extract_notes(texts, name),
            photoUrl="",
            confidence=max(
                [*(entry.confidence for entry in detection.detections), *(block.confidence or 0.0 for block in ocr_payload.blocks)],
                default=None,
            ),
            sourceText=_build_source_text(detection, ocr_payload),
        )
        items.append(item)
        recognized_fields.append(item.model_dump())

    return (
        MedicationRecognitionResponse(items=items),
        PipelineDebugSnapshot(
            detections=detection_payloads,
            ocrBlocks=ocr_payloads,
            recognizedFields=recognized_fields,
        ),
    )
