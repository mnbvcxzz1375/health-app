from __future__ import annotations

from pathlib import Path
from typing import List, Optional, Tuple

import cv2

from app.config import settings
from app.pose.evaluation import evaluate_request
from app.pose.utils import smooth_landmarks
from app.schemas import AnalyzeRequest, AnalyzeResponse, LandmarkModel, PoseFrameModel, Verdict

try:
    import mediapipe as mp
    from mediapipe.tasks.python import BaseOptions
    from mediapipe.tasks.python.vision import PoseLandmarker, PoseLandmarkerOptions, RunningMode
except ImportError:  # pragma: no cover
    mp = None
    BaseOptions = None
    PoseLandmarker = None
    PoseLandmarkerOptions = None
    RunningMode = None


class PoseAnalyzer:
    def __init__(self) -> None:
        self._landmarker = None

    def analyze(self, request: AnalyzeRequest) -> AnalyzeResponse:
        if mp is None or PoseLandmarker is None:
            return self._low_confidence(request, "mediapipe is not installed")
        if not Path(settings.model_path).exists():
            return self._low_confidence(request, f"MediaPipe model file not found: {settings.model_path}")
        if not Path(request.videoPath).exists():
            return self._low_confidence(request, f"Video file not found: {request.videoPath}")

        capture = cv2.VideoCapture(request.videoPath)
        if not capture.isOpened():
            return self._low_confidence(request, f"Unable to open video: {request.videoPath}")

        video_fps = capture.get(cv2.CAP_PROP_FPS) or 30.0
        total_frames = int(capture.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
        duration_seconds = total_frames / max(video_fps, 1.0)
        if duration_seconds > settings.max_video_seconds:
            capture.release()
            return self._low_confidence(request, f"Video is longer than supported limit ({settings.max_video_seconds}s)")

        sample_every = max(int(round(video_fps / max(settings.target_fps, 1.0))), 1)
        sampled_frame_count = 0
        valid_frame_count = 0
        frames: List[PoseFrameModel] = []
        previous_landmarks: Optional[List[LandmarkModel]] = None
        landmarker = self._get_landmarker()

        frame_index = 0
        try:
            while True:
                ok, frame = capture.read()
                if not ok:
                    break
                if frame_index % sample_every != 0:
                    frame_index += 1
                    continue

                sampled_frame_count += 1
                timestamp_ms = int((frame_index / max(video_fps, 1.0)) * 1000.0)
                rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
                result = landmarker.detect_for_video(mp_image, timestamp_ms)
                if not result.pose_landmarks:
                    frame_index += 1
                    continue

                image_landmarks = [
                    LandmarkModel(
                        x=float(item.x),
                        y=float(item.y),
                        z=float(item.z),
                        visibility=float(getattr(item, "visibility", 0.0) or 0.0),
                        presence=float(getattr(item, "presence", 0.0) or 0.0),
                    )
                    for item in result.pose_landmarks[0]
                ]
                if not image_landmarks:
                    frame_index += 1
                    continue

                smoothed = smooth_landmarks(image_landmarks, previous_landmarks, settings.ema_alpha)
                world_landmarks = [
                    LandmarkModel(
                        x=float(item.x),
                        y=float(item.y),
                        z=float(item.z),
                        visibility=float(getattr(item, "visibility", 0.0) or 0.0),
                        presence=float(getattr(item, "presence", 0.0) or 0.0),
                    )
                    for item in (result.pose_world_landmarks[0] if result.pose_world_landmarks else [])
                ]
                previous_landmarks = smoothed
                tracking_confidence = sum(item.visibility for item in smoothed) / max(len(smoothed), 1)
                if tracking_confidence < settings.min_visibility:
                    frame_index += 1
                    continue

                valid_frame_count += 1
                frames.append(
                    PoseFrameModel(
                        timestampMs=timestamp_ms,
                        landmarks=smoothed,
                        worldLandmarks=world_landmarks,
                        trackingConfidence=tracking_confidence,
                    )
                )
                frame_index += 1
        finally:
            capture.release()

        valid_frame_ratio = valid_frame_count / max(sampled_frame_count, 1)
        return evaluate_request(request, frames, valid_frame_ratio)

    def _get_landmarker(self):
        if self._landmarker is None:
            options = PoseLandmarkerOptions(
                base_options=BaseOptions(model_asset_path=settings.model_path),
                running_mode=RunningMode.VIDEO,
                num_poses=1,
                min_pose_detection_confidence=settings.min_visibility,
                min_pose_presence_confidence=settings.min_visibility,
                min_tracking_confidence=settings.min_visibility,
                output_segmentation_masks=False,
            )
            self._landmarker = PoseLandmarker.create_from_options(options)
        return self._landmarker

    def _low_confidence(self, request: AnalyzeRequest, reason: str) -> AnalyzeResponse:
        return AnalyzeResponse(
            exerciseType=request.exerciseType,
            score=0.0,
            verdict=Verdict.LOW_CONFIDENCE,
            validFrameRatio=0.0,
            failReason=reason,
            advice={
                "summary": "当前环境无法完成稳定体态识别，请检查模型文件、视频路径和推理依赖。",
                "warnings": [reason],
            },
        )
