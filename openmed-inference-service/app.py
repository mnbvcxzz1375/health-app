"""
OpenMed Local Inference Service
Loads Chinese Medical NER and PII models locally and serves via HTTP.

PII 模型支持切换：
- 默认使用蒸馏后的 DistilBERT-chinese（小、快）
- 设置环境变量 OPENMED_PII_USE_TEACHER=true 切回 OpenMed-PII teacher（Qwen 600M）
"""
import os
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import uvicorn

app = FastAPI(title="OpenMed Inference Service", version="1.2.0")

# Model pipelines - loaded on startup
ner_pipeline = None
pii_pipeline = None
pii_model_kind = "unknown"  # "teacher" | "student"


@app.on_event("startup")
async def load_models():
    global ner_pipeline, pii_pipeline, pii_model_kind
    from transformers import pipeline

    models_dir = os.path.join(os.path.dirname(__file__), "..", "backend-java", "models")

    # Chinese Medical NER model — supports Drug, Disease, Symptom, BodyParts entities
    ner_path = os.path.join(models_dir, "Chinese-Medical-NER")
    print(f"Loading Chinese Medical NER model from {ner_path}...")
    ner_pipeline = pipeline("token-classification", model=ner_path, aggregation_strategy="simple")
    print("Chinese Medical NER model loaded!")

    # PII 检测：默认 student（蒸馏后 DistilBERT-chinese），可切回 teacher
    use_teacher = os.getenv("OPENMED_PII_USE_TEACHER", "false").lower() == "true"
    student_path = os.path.join(models_dir, "OpenMed-PII-DistilBERT-chinese")
    teacher_path = os.path.join(models_dir, "OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1")

    if use_teacher:
        if not os.path.isdir(teacher_path):
            raise RuntimeError(
                f"OPENMED_PII_USE_TEACHER=true 但 teacher 模型目录不存在: {teacher_path}"
            )
        pii_path = teacher_path
        pii_model_kind = "teacher"
    else:
        if not os.path.isdir(student_path):
            # student 未蒸馏时自动回退到 teacher
            print(f"[WARN] student 模型目录不存在: {student_path}，回退到 teacher")
            pii_path = teacher_path
            pii_model_kind = "teacher"
        else:
            pii_path = student_path
            pii_model_kind = "student"

    print(f"Loading PII model ({pii_model_kind}) from {pii_path}...")
    pii_pipeline = pipeline("token-classification", model=pii_path, aggregation_strategy="simple")
    print(f"PII model ({pii_model_kind}) loaded!")


class NERRequest(BaseModel):
    text: str

class NEREntity(BaseModel):
    text: str
    label: str
    score: float
    start: int
    end: int

class NERResponse(BaseModel):
    entities: List[NEREntity]

class PIIRequest(BaseModel):
    text: str

class PIIMask(BaseModel):
    text: str
    label: str
    score: float
    start: int
    end: int

class PIIResponse(BaseModel):
    masks: List[PIIMask]


@app.post("/ner/extract", response_model=NERResponse)
async def extract_entities(request: NERRequest):
    if ner_pipeline is None:
        raise HTTPException(503, "NER model not loaded yet")

    results = ner_pipeline(request.text)
    entities = []
    for r in results:
        # Fix space-separated Chinese characters in word field
        word = r["word"].replace(" ", "")
        # Reconstruct from original text using offsets for accuracy
        if r["start"] < len(request.text) and r["end"] <= len(request.text):
            word = request.text[r["start"]:r["end"]]
        entities.append(NEREntity(
            text=word,
            label=r["entity_group"],
            score=round(float(r["score"]), 4),
            start=r["start"],
            end=r["end"]
        ))
    return NERResponse(entities=entities)


@app.post("/pii/detect", response_model=PIIResponse)
async def detect_pii(request: PIIRequest):
    if pii_pipeline is None:
        raise HTTPException(503, "PII model not loaded yet")

    results = pii_pipeline(request.text)
    masks = []
    for r in results:
        masks.append(PIIMask(
            text=r["word"],
            label=r["entity_group"],
            score=round(r["score"], 4),
            start=r["start"],
            end=r["end"]
        ))
    return PIIResponse(masks=masks)


@app.get("/health")
async def health():
    return {
        "status": "UP",
        "ner_loaded": ner_pipeline is not None,
        "pii_loaded": pii_pipeline is not None,
        "pii_model_kind": pii_model_kind,
    }


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8012)
