from __future__ import annotations

from math import acos, degrees
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

import cv2
import numpy as np

from app.pose.landmarks import SIDE_INDICES
from app.schemas import EvidenceFrameModel, LandmarkModel, PoseFrameModel


def landmark_angle(a: LandmarkModel, b: LandmarkModel, c: LandmarkModel) -> float:
    ab = np.array([a.x - b.x, a.y - b.y, a.z - b.z], dtype=float)
    cb = np.array([c.x - b.x, c.y - b.y, c.z - b.z], dtype=float)
    denom = np.linalg.norm(ab) * np.linalg.norm(cb)
    if denom < 1e-6:
        return 180.0
    cosine = float(np.clip(np.dot(ab, cb) / denom, -1.0, 1.0))
    return degrees(acos(cosine))


def line_angle_from_vertical(top: LandmarkModel, bottom: LandmarkModel) -> float:
    dx = bottom.x - top.x
    dy = bottom.y - top.y
    return degrees(acos(np.clip(abs(dy) / max((dx * dx + dy * dy) ** 0.5, 1e-6), -1.0, 1.0)))


def select_dominant_side(frames: Sequence[PoseFrameModel]) -> str:
    scores: Dict[str, float] = {"left": 0.0, "right": 0.0}
    for frame in frames:
        for side, indices in SIDE_INDICES.items():
            side_score = 0.0
            for _, idx in indices.items():
                if idx < len(frame.landmarks):
                    side_score += frame.landmarks[idx].visibility
            scores[side] += side_score
    return "left" if scores["left"] >= scores["right"] else "right"


def smooth_landmarks(raw_landmarks: Sequence[LandmarkModel], previous: Optional[Sequence[LandmarkModel]], alpha: float) -> List[LandmarkModel]:
    if not previous or len(previous) != len(raw_landmarks):
        return list(raw_landmarks)

    smoothed: List[LandmarkModel] = []
    for prev, current in zip(previous, raw_landmarks):
        smoothed.append(
            LandmarkModel(
                x=prev.x * (1.0 - alpha) + current.x * alpha,
                y=prev.y * (1.0 - alpha) + current.y * alpha,
                z=prev.z * (1.0 - alpha) + current.z * alpha,
                visibility=current.visibility,
                presence=current.presence,
            )
        )
    return smoothed


def extract_series(values: Iterable[Tuple[int, float]]) -> Tuple[List[int], List[float]]:
    timestamps: List[int] = []
    metrics: List[float] = []
    for timestamp, value in values:
        timestamps.append(timestamp)
        metrics.append(value)
    return timestamps, metrics


def detect_rep_segments(metric_values: Sequence[float], top_threshold: float, bottom_threshold: float) -> List[Tuple[int, int, int]]:
    segments: List[Tuple[int, int, int]] = []
    in_rep = False
    start_idx = 0
    min_idx = 0
    min_value = 999.0
    for idx, value in enumerate(metric_values):
        if not in_rep and value < top_threshold:
            in_rep = True
            start_idx = max(0, idx - 1)
            min_idx = idx
            min_value = value
            continue

        if in_rep:
            if value < min_value:
                min_value = value
                min_idx = idx
            if value > top_threshold and idx - start_idx >= 3:
                if min_value <= bottom_threshold:
                    segments.append((start_idx, min_idx, idx))
                in_rep = False

    if in_rep and min_value <= bottom_threshold and len(metric_values) - start_idx >= 3:
        segments.append((start_idx, min_idx, len(metric_values) - 1))
    return segments


def rep_score_from_issue_count(issues) -> float:
    penalty_map = {"MAJOR": 15, "MEDIUM": 8, "MINOR": 4}
    total_penalty = sum(penalty_map.get(issue.severity.value, 4) for issue in issues)
    return max(0.0, 100.0 - total_penalty)


def save_evidence_frames(video_path: str, evidence_output_dir: str, evidence_items: Sequence[Tuple[str, int]]) -> List[EvidenceFrameModel]:
    evidence_dir = Path(evidence_output_dir)
    evidence_dir.mkdir(parents=True, exist_ok=True)
    capture = cv2.VideoCapture(video_path)
    if not capture.isOpened():
        return []

    root_dir = evidence_dir.parents[2] if len(evidence_dir.parents) >= 3 else evidence_dir.parent
    saved: List[EvidenceFrameModel] = []
    seen = set()
    try:
        for index, (label, timestamp_ms) in enumerate(evidence_items, start=1):
            dedupe_key = (label, int(timestamp_ms))
            if dedupe_key in seen:
                continue
            seen.add(dedupe_key)
            capture.set(cv2.CAP_PROP_POS_MSEC, max(float(timestamp_ms), 0.0))
            ok, frame = capture.read()
            if not ok:
                continue
            filename = f"{index:02d}_{label.lower()}.jpg"
            target = evidence_dir / filename
            cv2.imwrite(str(target), frame)
            saved.append(
                EvidenceFrameModel(
                    label=label,
                    timestampMs=int(timestamp_ms),
                    imageUrl=target.relative_to(root_dir).as_posix(),
                )
            )
    finally:
        capture.release()
    return saved
