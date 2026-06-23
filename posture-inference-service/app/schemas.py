from enum import Enum
from typing import List, Optional

from pydantic import BaseModel, Field


class ExerciseType(str, Enum):
    SQUAT = "SQUAT"
    PUSH_UP = "PUSH_UP"
    PLANK = "PLANK"
    LUNGE = "LUNGE"


class CameraView(str, Enum):
    SIDE = "SIDE"
    FRONT = "FRONT"
    ANGLED = "ANGLED"


class Severity(str, Enum):
    MAJOR = "MAJOR"
    MEDIUM = "MEDIUM"
    MINOR = "MINOR"


class Verdict(str, Enum):
    STANDARD = "STANDARD"
    NEEDS_IMPROVEMENT = "NEEDS_IMPROVEMENT"
    LOW_CONFIDENCE = "LOW_CONFIDENCE"


class LandmarkModel(BaseModel):
    x: float
    y: float
    z: float
    visibility: float = 0.0
    presence: float = 0.0


class PoseFrameModel(BaseModel):
    timestampMs: int
    landmarks: List[LandmarkModel] = Field(default_factory=list)
    worldLandmarks: List[LandmarkModel] = Field(default_factory=list)
    trackingConfidence: float = 0.0


class FormIssueModel(BaseModel):
    code: str
    severity: Severity
    phase: str
    metricName: str
    actualValue: float
    targetRange: str
    evidenceTimestampMs: int
    description: str


class RepAnalysisModel(BaseModel):
    repIndex: int
    startMs: int
    endMs: int
    score: float
    issues: List[FormIssueModel] = Field(default_factory=list)


class EvidenceFrameModel(BaseModel):
    label: str
    timestampMs: int
    imageUrl: Optional[str] = None


class AdviceModel(BaseModel):
    summary: Optional[str] = None
    suggestions: List[str] = Field(default_factory=list)
    warnings: List[str] = Field(default_factory=list)


class AnalyzeRequest(BaseModel):
    jobId: str
    videoPath: str
    evidenceOutputDir: str
    exerciseType: ExerciseType
    cameraView: CameraView


class AnalyzeResponse(BaseModel):
    provider: str = "mediapipe_pose_landmarker"
    exerciseType: ExerciseType
    score: float
    verdict: Verdict
    validFrameRatio: float
    failReason: Optional[str] = None
    frames: List[PoseFrameModel] = Field(default_factory=list)
    issues: List[FormIssueModel] = Field(default_factory=list)
    reps: List[RepAnalysisModel] = Field(default_factory=list)
    evidenceFrames: List[EvidenceFrameModel] = Field(default_factory=list)
    advice: AdviceModel = Field(default_factory=AdviceModel)
