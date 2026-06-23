from dataclasses import dataclass
import os
from pathlib import Path


@dataclass
class Settings:
    model_path: str = os.getenv(
        "MEDIAPIPE_MODEL_PATH",
        str(Path(__file__).resolve().parents[1] / "models" / "pose_landmarker_full.task"),
    )
    min_visibility: float = float(os.getenv("MIN_VISIBILITY", "0.55"))
    target_fps: float = float(os.getenv("TARGET_FPS", "12"))
    ema_alpha: float = float(os.getenv("EMA_ALPHA", "0.35"))
    max_video_seconds: int = int(os.getenv("MAX_VIDEO_SECONDS", "60"))
    redis_url: str = os.getenv("POSTURE_REDIS_URL", "redis://127.0.0.1:6379/0")
    redis_queue_key: str = os.getenv("POSTURE_QUEUE_KEY", "posture:jobs")
    posture_callback_url: str = os.getenv("POSTURE_CALLBACK_URL", "http://127.0.0.1:8080")
    queue_block_seconds: int = int(os.getenv("POSTURE_QUEUE_BLOCK_SECONDS", "3"))
    callback_timeout_seconds: int = int(os.getenv("POSTURE_CALLBACK_TIMEOUT_SECONDS", "60"))


settings = Settings()
