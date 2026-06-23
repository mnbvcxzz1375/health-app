from __future__ import annotations

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from local_medication_api.config import settings
from local_medication_api.models import HealthResponse, MedicationRecognitionResponse
from local_medication_api.services.detector import detector
from local_medication_api.services.formatter import build_response
from local_medication_api.services.ocr_adapter import ocr_adapter


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="Independent local medication recognition sidecar kept under E:\\VScodeProject\\health-app.",
)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        service=settings.app_name,
        version=settings.app_version,
        model_ready=detector.is_ready(),
        weights_path=str(settings.weights_path),
        ocr_provider=ocr_adapter.provider,
        scene=settings.scene_name,
    )


@app.post("/recognize/medications", response_model=MedicationRecognitionResponse)
async def recognize_medications(
    files: list[UploadFile] = File(...),
    scene: str = Form(...),
) -> MedicationRecognitionResponse:
    if scene != settings.scene_name:
        raise HTTPException(status_code=400, detail=f"scene must be '{settings.scene_name}'")

    if not files:
        raise HTTPException(status_code=400, detail="At least one image is required")

    detection_payloads = []
    for file in files[:8]:
        if not file.content_type or not file.content_type.startswith("image/"):
            raise HTTPException(status_code=400, detail=f"Unsupported file type: {file.filename}")
        data = await file.read()
        if not data:
            raise HTTPException(status_code=400, detail=f"Empty file: {file.filename}")
        detection_payloads.append(
            detector.detect(
                image_bytes=data,
                filename=file.filename or "upload.bin",
                content_type=file.content_type,
            )
        )

    response, _debug = build_response(detection_payloads, ocr_adapter.extract(detection_payloads))
    return response
