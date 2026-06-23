import json
import threading
from typing import Any, Dict

import redis
import requests

from app.config import Settings
from app.pose.analyzer import PoseAnalyzer
from app.schemas import AnalyzeRequest


class PostureQueueWorker:
    def __init__(self, settings: Settings, analyzer: PoseAnalyzer) -> None:
        self.settings = settings
        self.analyzer = analyzer
        self._thread = None
        self._stop = threading.Event()

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._thread = threading.Thread(target=self._consume_forever, name="posture-queue-worker", daemon=True)
        self._thread.start()

    def _consume_forever(self) -> None:
        client = redis.Redis.from_url(self.settings.redis_url, decode_responses=True)
        while not self._stop.is_set():
            message = client.brpop(self.settings.redis_queue_key, timeout=self.settings.queue_block_seconds)
            if not message:
                continue

            _, raw_payload = message
            payload = json.loads(raw_payload)
            job_id = str(payload.get("jobId", "")).strip()
            callback_url = self.settings.posture_callback_url.rstrip("/") + "/internal/v1/posture/jobs/" + job_id + "/result"

            try:
                request = AnalyzeRequest(
                    jobId=job_id,
                    videoPath=str(payload.get("videoPath", "")),
                    evidenceOutputDir=str(payload.get("evidenceOutputDir", "")),
                    exerciseType=str(payload.get("exerciseType", "PLANK")),
                    cameraView=str(payload.get("cameraView", "SIDE")),
                )
                response = self.analyzer.analyze(request)
                status = "LOW_CONFIDENCE" if response.verdict == "LOW_CONFIDENCE" else "SUCCEEDED"
                callback_payload: Dict[str, Any] = {
                    "status": status,
                    "progress": 100,
                    "failReason": response.failReason,
                    "analysis": response.model_dump(mode="json"),
                }
            except Exception as exc:  # pragma: no cover
                callback_payload = {
                    "status": "FAILED",
                    "progress": 100,
                    "failReason": str(exc),
                    "analysis": None,
                }

            requests.post(
                callback_url,
                json=callback_payload,
                timeout=self.settings.callback_timeout_seconds,
            )
