from fastapi import FastAPI

from app.config import settings
from app.pose.analyzer import PoseAnalyzer
from app.queue_worker import PostureQueueWorker
from app.schemas import AnalyzeRequest, AnalyzeResponse

app = FastAPI(title="posture-inference-service", version="0.1.0")
analyzer = PoseAnalyzer()
queue_worker = PostureQueueWorker(settings=settings, analyzer=analyzer)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/internal/v1/pose/analyze", response_model=AnalyzeResponse)
def analyze_pose(request: AnalyzeRequest) -> AnalyzeResponse:
    return analyzer.analyze(request)


@app.on_event("startup")
def start_queue_worker() -> None:
    queue_worker.start()
