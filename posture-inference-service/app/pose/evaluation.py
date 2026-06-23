from __future__ import annotations

from statistics import mean, pstdev
from typing import List, Sequence, Tuple

from app.pose.landmarks import NOSE, SIDE_INDICES
from app.pose.utils import detect_rep_segments, landmark_angle, line_angle_from_vertical, rep_score_from_issue_count, save_evidence_frames, select_dominant_side
from app.schemas import AnalyzeRequest, AnalyzeResponse, AdviceModel, FormIssueModel, PoseFrameModel, RepAnalysisModel, Severity, Verdict


def evaluate_request(request: AnalyzeRequest, frames: Sequence[PoseFrameModel], valid_frame_ratio: float) -> AnalyzeResponse:
    if valid_frame_ratio < 0.7 or not frames:
        return AnalyzeResponse(
            exerciseType=request.exerciseType,
            score=0.0,
            verdict=Verdict.LOW_CONFIDENCE,
            validFrameRatio=valid_frame_ratio,
            failReason="Pose landmarks are unstable or too sparse for a reliable judgment.",
            frames=list(frames),
            advice=AdviceModel(
                summary="关键点有效率不足，建议改善拍摄角度和光线后重新分析。",
                warnings=["当前视频更适合作为参考样本，不建议直接据此做训练纠正。"],
            ),
        )

    side = select_dominant_side(frames)
    if request.exerciseType.value == "PLANK":
        return _evaluate_plank(request, frames, valid_frame_ratio, side)
    if request.exerciseType.value == "SQUAT":
        return _evaluate_squat(request, frames, valid_frame_ratio, side)
    if request.exerciseType.value == "PUSH_UP":
        return _evaluate_push_up(request, frames, valid_frame_ratio, side)
    return _evaluate_lunge(request, frames, valid_frame_ratio, side)


def _evaluate_squat(request: AnalyzeRequest, frames: Sequence[PoseFrameModel], valid_frame_ratio: float, side: str) -> AnalyzeResponse:
    metrics = []
    indices = SIDE_INDICES[side]
    for frame in frames:
        knee = landmark_angle(frame.landmarks[indices["hip"]], frame.landmarks[indices["knee"]], frame.landmarks[indices["ankle"]])
        torso_lean = line_angle_from_vertical(frame.landmarks[indices["shoulder"]], frame.landmarks[indices["hip"]])
        hip_extension = landmark_angle(frame.landmarks[indices["shoulder"]], frame.landmarks[indices["hip"]], frame.landmarks[indices["knee"]])
        heel_lift = max(0.0, (frame.landmarks[indices["ankle"]].y - frame.landmarks[indices["heel"]].y) * 1000.0)
        metrics.append((frame.timestampMs, knee, torso_lean, hip_extension, heel_lift))

    rep_boundaries = detect_rep_segments([item[1] for item in metrics], top_threshold=150.0, bottom_threshold=120.0)
    return _score_dynamic_reps(request, frames, valid_frame_ratio, metrics, rep_boundaries, side, "squat")


def _evaluate_push_up(request: AnalyzeRequest, frames: Sequence[PoseFrameModel], valid_frame_ratio: float, side: str) -> AnalyzeResponse:
    metrics = []
    indices = SIDE_INDICES[side]
    for frame in frames:
        elbow = landmark_angle(frame.landmarks[indices["shoulder"]], frame.landmarks[indices["elbow"]], frame.landmarks[indices["wrist"]])
        body_line = landmark_angle(frame.landmarks[indices["shoulder"]], frame.landmarks[indices["hip"]], frame.landmarks[indices["ankle"]])
        hip_delta = ((frame.landmarks[indices["hip"]].y - frame.landmarks[indices["shoulder"]].y) - (frame.landmarks[indices["ankle"]].y - frame.landmarks[indices["hip"]].y)) * 1000.0
        metrics.append((frame.timestampMs, elbow, body_line, hip_delta))

    rep_boundaries = detect_rep_segments([item[1] for item in metrics], top_threshold=155.0, bottom_threshold=105.0)
    return _score_dynamic_reps(request, frames, valid_frame_ratio, metrics, rep_boundaries, side, "push_up")


def _evaluate_lunge(request: AnalyzeRequest, frames: Sequence[PoseFrameModel], valid_frame_ratio: float, side: str) -> AnalyzeResponse:
    metrics = []
    indices = SIDE_INDICES[side]
    for frame in frames:
        knee = landmark_angle(frame.landmarks[indices["hip"]], frame.landmarks[indices["knee"]], frame.landmarks[indices["ankle"]])
        torso_lean = line_angle_from_vertical(frame.landmarks[indices["shoulder"]], frame.landmarks[indices["hip"]])
        hip_extension = landmark_angle(frame.landmarks[indices["shoulder"]], frame.landmarks[indices["hip"]], frame.landmarks[indices["knee"]])
        metrics.append((frame.timestampMs, knee, torso_lean, hip_extension))

    rep_boundaries = detect_rep_segments([item[1] for item in metrics], top_threshold=155.0, bottom_threshold=120.0)
    return _score_dynamic_reps(request, frames, valid_frame_ratio, metrics, rep_boundaries, side, "lunge")


def _evaluate_plank(request: AnalyzeRequest, frames: Sequence[PoseFrameModel], valid_frame_ratio: float, side: str) -> AnalyzeResponse:
    indices = SIDE_INDICES[side]
    issues: List[FormIssueModel] = []
    body_angles = []
    hip_offsets = []
    neck_angles = []
    for frame in frames:
        body_angle = landmark_angle(frame.landmarks[indices["shoulder"]], frame.landmarks[indices["hip"]], frame.landmarks[indices["ankle"]])
        hip_offset = ((frame.landmarks[indices["hip"]].y - frame.landmarks[indices["shoulder"]].y) - (frame.landmarks[indices["ankle"]].y - frame.landmarks[indices["hip"]].y)) * 1000.0
        neck_angle = landmark_angle(frame.landmarks[NOSE], frame.landmarks[indices["shoulder"]], frame.landmarks[indices["hip"]])
        body_angles.append((frame.timestampMs, body_angle))
        hip_offsets.append((frame.timestampMs, hip_offset))
        neck_angles.append((frame.timestampMs, neck_angle))

    min_body_angle = min(body_angles, key=lambda item: item[1])
    min_hip_offset = min(hip_offsets, key=lambda item: item[1])
    max_hip_offset = max(hip_offsets, key=lambda item: item[1])
    min_neck_angle = min(neck_angles, key=lambda item: item[1])

    if min_hip_offset[1] < -70.0:
        issues.append(_issue("PLANK_HIP_SAG", Severity.MAJOR, "hold", "hipOffset", min_hip_offset[1], ">= -70", min_hip_offset[0], "骨盆低于理想中立位，出现塌腰趋势。"))
    if max_hip_offset[1] > 70.0:
        issues.append(_issue("PLANK_HIP_PIKE", Severity.MEDIUM, "hold", "hipOffset", max_hip_offset[1], "<= 70", max_hip_offset[0], "臀部抬得过高，身体没有保持一条直线。"))
    if min_body_angle[1] < 155.0:
        issues.append(_issue("PLANK_BODY_LINE_BREAK", Severity.MEDIUM, "hold", "bodyLineAngle", min_body_angle[1], ">= 155", min_body_angle[0], "肩髋踝连线不够稳定。"))
    if min_neck_angle[1] < 140.0:
        issues.append(_issue("PLANK_NECK_MISALIGNED", Severity.MINOR, "hold", "neckAngle", min_neck_angle[1], ">= 140", min_neck_angle[0], "头颈与躯干没有保持同向延展。"))

    rep = RepAnalysisModel(repIndex=1, startMs=frames[0].timestampMs, endMs=frames[-1].timestampMs, score=rep_score_from_issue_count(issues), issues=issues)
    evidence = save_evidence_frames(request.videoPath, request.evidenceOutputDir, [(issue.code, issue.evidenceTimestampMs) for issue in issues])
    score = round(rep.score * valid_frame_ratio, 2)
    verdict = Verdict.STANDARD if score >= 85.0 and not issues else Verdict.NEEDS_IMPROVEMENT
    return AnalyzeResponse(
        exerciseType=request.exerciseType,
        score=score,
        verdict=verdict,
        validFrameRatio=valid_frame_ratio,
        frames=list(frames),
        issues=issues,
        reps=[rep],
        evidenceFrames=evidence,
        advice=_advice(verdict, issues),
    )


def _score_dynamic_reps(request: AnalyzeRequest, frames: Sequence[PoseFrameModel], valid_frame_ratio: float, metrics: Sequence[Tuple], rep_boundaries: Sequence[Tuple[int, int, int]], side: str, mode: str) -> AnalyzeResponse:
    if not rep_boundaries:
        return AnalyzeResponse(
            exerciseType=request.exerciseType,
            score=0.0,
            verdict=Verdict.LOW_CONFIDENCE,
            validFrameRatio=valid_frame_ratio,
            failReason="Unable to segment repetitions reliably from the uploaded video.",
            frames=list(frames),
            advice=AdviceModel(
                summary="没有稳定识别出完整动作周期，建议放慢节奏并确保全身持续入镜。",
                warnings=["请优先使用侧视角并避免多人同框。"],
            ),
        )

    rep_results: List[RepAnalysisModel] = []
    all_issues: List[FormIssueModel] = []
    evidence_requests: List[Tuple[str, int]] = []
    rep_durations: List[int] = []
    for rep_index, (start_idx, bottom_idx, end_idx) in enumerate(rep_boundaries, start=1):
        start_metric = metrics[start_idx]
        bottom_metric = metrics[bottom_idx]
        end_metric = metrics[end_idx]
        rep_durations.append(end_metric[0] - start_metric[0])
        issues = _rep_issues(mode, metrics, start_metric, bottom_metric, end_metric)
        rep = RepAnalysisModel(
            repIndex=rep_index,
            startMs=start_metric[0],
            endMs=end_metric[0],
            score=rep_score_from_issue_count(issues),
            issues=issues,
        )
        rep_results.append(rep)
        all_issues.extend(issues)
        evidence_requests.extend((issue.code, issue.evidenceTimestampMs) for issue in issues)

    if mode == "lunge" and len(rep_durations) >= 2:
        avg_duration = mean(rep_durations)
        duration_std = pstdev(rep_durations)
        if avg_duration > 0 and (duration_std / avg_duration) > 0.25:
            unstable_ts = rep_results[0].startMs
            issue = _issue("LUNGE_RHYTHM_UNSTABLE", Severity.MINOR, "tempo", "repDurationCv", duration_std / avg_duration, "<= 0.25", unstable_ts, "前后腿切换节奏不够稳定。")
            all_issues.append(issue)
            rep_results[0].issues.append(issue)
            rep_results[0].score = rep_score_from_issue_count(rep_results[0].issues)
            evidence_requests.append((issue.code, issue.evidenceTimestampMs))

    evidence = save_evidence_frames(request.videoPath, request.evidenceOutputDir, evidence_requests)
    rep_scores = [rep.score for rep in rep_results] or [0.0]
    score = round(mean(rep_scores) * valid_frame_ratio, 2)
    verdict = Verdict.STANDARD if score >= 85.0 and not all_issues else Verdict.NEEDS_IMPROVEMENT
    return AnalyzeResponse(
        exerciseType=request.exerciseType,
        score=score,
        verdict=verdict,
        validFrameRatio=valid_frame_ratio,
        frames=list(frames),
        issues=all_issues,
        reps=rep_results,
        evidenceFrames=evidence,
        advice=_advice(verdict, all_issues),
    )


def _rep_issues(mode: str, metrics: Sequence[Tuple], start_metric: Tuple, bottom_metric: Tuple, end_metric: Tuple) -> List[FormIssueModel]:
    issues: List[FormIssueModel] = []
    if mode == "squat":
        if bottom_metric[1] > 100.0:
            issues.append(_issue("SQUAT_DEPTH_INSUFFICIENT", Severity.MAJOR, "bottom", "kneeAngle", bottom_metric[1], "<= 100", bottom_metric[0], "下蹲深度不足。"))
        if bottom_metric[2] > 35.0:
            issues.append(_issue("SQUAT_TRUNK_LEAN", Severity.MEDIUM, "bottom", "torsoLean", bottom_metric[2], "<= 35", bottom_metric[0], "下蹲时躯干前倾过大。"))
        if max(start_metric[3], end_metric[3]) < 165.0:
            issues.append(_issue("SQUAT_HIP_EXTENSION_INCOMPLETE", Severity.MEDIUM, "top", "hipAngle", max(start_metric[3], end_metric[3]), ">= 165", end_metric[0], "起身后髋部没有完全伸展。"))
        if bottom_metric[4] > 30.0:
            issues.append(_issue("SQUAT_HEEL_RISE", Severity.MINOR, "bottom", "heelLift", bottom_metric[4], "<= 30", bottom_metric[0], "下蹲过程中脚跟有明显抬起。"))
    elif mode == "push_up":
        if bottom_metric[1] > 95.0:
            issues.append(_issue("PUSH_UP_DEPTH_INSUFFICIENT", Severity.MAJOR, "bottom", "elbowAngle", bottom_metric[1], "<= 95", bottom_metric[0], "下放深度不足。"))
        if bottom_metric[3] < -80.0:
            issues.append(_issue("PUSH_UP_HIP_SAG", Severity.MAJOR, "bottom", "hipOffset", bottom_metric[3], ">= -80", bottom_metric[0], "下放时腰部有塌陷趋势。"))
        if bottom_metric[3] > 80.0:
            issues.append(_issue("PUSH_UP_HIP_PIKE", Severity.MEDIUM, "bottom", "hipOffset", bottom_metric[3], "<= 80", bottom_metric[0], "下放时臀部抬得过高。"))
        if max(start_metric[1], end_metric[1]) < 160.0:
            issues.append(_issue("PUSH_UP_LOCKOUT_INCOMPLETE", Severity.MINOR, "top", "elbowAngle", max(start_metric[1], end_metric[1]), ">= 160", end_metric[0], "顶部没有完成手肘伸直。"))
    elif mode == "lunge":
        if bottom_metric[1] > 100.0:
            issues.append(_issue("LUNGE_DEPTH_INSUFFICIENT", Severity.MAJOR, "bottom", "kneeAngle", bottom_metric[1], "<= 100", bottom_metric[0], "下蹲深度不足。"))
        if bottom_metric[2] > 30.0:
            issues.append(_issue("LUNGE_TRUNK_LEAN", Severity.MEDIUM, "bottom", "torsoLean", bottom_metric[2], "<= 30", bottom_metric[0], "下蹲时躯干前倾过大。"))
        if max(start_metric[3], end_metric[3]) < 165.0:
            issues.append(_issue("LUNGE_EXTENSION_INCOMPLETE", Severity.MEDIUM, "top", "hipAngle", max(start_metric[3], end_metric[3]), ">= 165", end_metric[0], "顶部未完全伸展。"))
    return issues


def _issue(code: str, severity: Severity, phase: str, metric_name: str, actual_value: float, target_range: str, timestamp_ms: int, description: str) -> FormIssueModel:
    return FormIssueModel(
        code=code,
        severity=severity,
        phase=phase,
        metricName=metric_name,
        actualValue=round(float(actual_value), 2),
        targetRange=target_range,
        evidenceTimestampMs=int(timestamp_ms),
        description=description,
    )


def _advice(verdict: Verdict, issues: Sequence[FormIssueModel]) -> AdviceModel:
    if verdict == Verdict.STANDARD:
        return AdviceModel(summary="动作整体标准度较高。", suggestions=["继续保持当前节奏和机位，作为后续对比基线。"])
    if not issues:
        return AdviceModel(summary="动作存在可改进点，但需要结合更多样本进一步确认。")
    suggestions = []
    seen = set()
    for issue in issues:
        if issue.code in seen:
            continue
        seen.add(issue.code)
        suggestions.append(issue.description)
    return AdviceModel(summary=f"检测到 {len(issues)} 个动作问题，建议优先修正高严重度项目。", suggestions=suggestions[:4])
