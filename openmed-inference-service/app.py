"""
OpenMed Local Inference Service
Loads Chinese Medical NER and PII models locally and serves via HTTP.
"""
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional
import uvicorn

app = FastAPI(title="OpenMed Inference Service", version="1.1.0")

# Model pipelines - loaded on startup
ner_pipeline = None
pii_pipeline = None

@app.on_event("startup")
async def load_models():
    global ner_pipeline, pii_pipeline
    from transformers import pipeline
    import os

    models_dir = os.path.join(os.path.dirname(__file__), "..", "backend-java", "models")

    # Chinese Medical NER model — supports Drug, Disease, Symptom, BodyParts entities
    ner_path = os.path.join(models_dir, "Chinese-Medical-NER")
    print(f"Loading Chinese Medical NER model from {ner_path}...")
    ner_pipeline = pipeline("token-classification", model=ner_path, aggregation_strategy="simple")
    print("Chinese Medical NER model loaded!")

    # PII detection model for Chinese text
    pii_path = os.path.join(models_dir, "OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1")
    print(f"Loading PII model from {pii_path}...")
    pii_pipeline = pipeline("token-classification", model=pii_path, aggregation_strategy="simple")
    print("PII model loaded!")


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
        "pii_loaded": pii_pipeline is not None
    }


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8012)
