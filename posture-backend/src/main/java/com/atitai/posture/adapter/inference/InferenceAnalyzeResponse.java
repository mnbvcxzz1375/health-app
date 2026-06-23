package com.atitai.posture.adapter.inference;

import com.atitai.posture.domain.AdviceBlock;
import com.atitai.posture.domain.EvidenceFrame;
import com.atitai.posture.domain.ExerciseType;
import com.atitai.posture.domain.FormIssue;
import com.atitai.posture.domain.Landmark;
import com.atitai.posture.domain.PoseFrame;
import com.atitai.posture.domain.PoseInferenceResult;
import com.atitai.posture.domain.PostureAnalysis;
import com.atitai.posture.domain.RepAnalysis;
import com.atitai.posture.domain.Severity;
import com.atitai.posture.domain.Verdict;
import java.util.ArrayList;
import java.util.List;

public class InferenceAnalyzeResponse {

    private String provider;
    private String exerciseType;
    private double score;
    private String verdict;
    private double validFrameRatio;
    private String failReason;
    private List<PoseFrameDto> frames = new ArrayList<PoseFrameDto>();
    private List<FormIssueDto> issues = new ArrayList<FormIssueDto>();
    private List<RepAnalysisDto> reps = new ArrayList<RepAnalysisDto>();
    private List<EvidenceFrameDto> evidenceFrames = new ArrayList<EvidenceFrameDto>();
    private AdviceDto advice = new AdviceDto();

    public PoseInferenceResult toDomain() {
        PoseInferenceResult result = new PoseInferenceResult();
        result.setProvider(provider);
        result.setFailReason(failReason);

        List<PoseFrame> poseFrames = new ArrayList<PoseFrame>();
        for (PoseFrameDto frame : frames) {
            poseFrames.add(frame.toDomain());
        }
        result.setFrames(poseFrames);

        PostureAnalysis analysis = new PostureAnalysis();
        analysis.setExerciseType(exerciseType == null ? null : ExerciseType.fromValue(exerciseType));
        analysis.setScore(score);
        analysis.setVerdict(verdict == null ? Verdict.LOW_CONFIDENCE : Verdict.valueOf(verdict));
        analysis.setValidFrameRatio(validFrameRatio);

        List<FormIssue> mappedIssues = new ArrayList<FormIssue>();
        for (FormIssueDto issue : issues) {
            mappedIssues.add(issue.toDomain());
        }
        analysis.setIssues(mappedIssues);

        List<RepAnalysis> mappedReps = new ArrayList<RepAnalysis>();
        for (RepAnalysisDto rep : reps) {
            mappedReps.add(rep.toDomain());
        }
        analysis.setReps(mappedReps);

        List<EvidenceFrame> mappedEvidence = new ArrayList<EvidenceFrame>();
        for (EvidenceFrameDto evidenceFrame : evidenceFrames) {
            mappedEvidence.add(evidenceFrame.toDomain());
        }
        analysis.setEvidenceFrames(mappedEvidence);

        AdviceBlock adviceBlock = advice == null ? new AdviceBlock() : advice.toDomain();
        analysis.setSummary(adviceBlock.getSummary());
        analysis.setSuggestions(adviceBlock.getSuggestions());
        analysis.setWarnings(adviceBlock.getWarnings());

        result.setPreliminaryAnalysis(analysis);
        return result;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getExerciseType() {
        return exerciseType;
    }

    public void setExerciseType(String exerciseType) {
        this.exerciseType = exerciseType;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public double getValidFrameRatio() {
        return validFrameRatio;
    }

    public void setValidFrameRatio(double validFrameRatio) {
        this.validFrameRatio = validFrameRatio;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public List<PoseFrameDto> getFrames() {
        return frames;
    }

    public void setFrames(List<PoseFrameDto> frames) {
        this.frames = frames;
    }

    public List<FormIssueDto> getIssues() {
        return issues;
    }

    public void setIssues(List<FormIssueDto> issues) {
        this.issues = issues;
    }

    public List<RepAnalysisDto> getReps() {
        return reps;
    }

    public void setReps(List<RepAnalysisDto> reps) {
        this.reps = reps;
    }

    public List<EvidenceFrameDto> getEvidenceFrames() {
        return evidenceFrames;
    }

    public void setEvidenceFrames(List<EvidenceFrameDto> evidenceFrames) {
        this.evidenceFrames = evidenceFrames;
    }

    public AdviceDto getAdvice() {
        return advice;
    }

    public void setAdvice(AdviceDto advice) {
        this.advice = advice;
    }

    public static class PoseFrameDto {
        private long timestampMs;
        private double trackingConfidence;
        private List<LandmarkDto> landmarks = new ArrayList<LandmarkDto>();
        private List<LandmarkDto> worldLandmarks = new ArrayList<LandmarkDto>();

        public PoseFrame toDomain() {
            PoseFrame frame = new PoseFrame();
            frame.setTimestampMs(timestampMs);
            frame.setTrackingConfidence(trackingConfidence);
            frame.setLandmarks(toLandmarks(landmarks));
            frame.setWorldLandmarks(toLandmarks(worldLandmarks));
            return frame;
        }

        private List<Landmark> toLandmarks(List<LandmarkDto> source) {
            List<Landmark> mapped = new ArrayList<Landmark>();
            for (LandmarkDto item : source) {
                mapped.add(item.toDomain());
            }
            return mapped;
        }

        public long getTimestampMs() {
            return timestampMs;
        }

        public void setTimestampMs(long timestampMs) {
            this.timestampMs = timestampMs;
        }

        public double getTrackingConfidence() {
            return trackingConfidence;
        }

        public void setTrackingConfidence(double trackingConfidence) {
            this.trackingConfidence = trackingConfidence;
        }

        public List<LandmarkDto> getLandmarks() {
            return landmarks;
        }

        public void setLandmarks(List<LandmarkDto> landmarks) {
            this.landmarks = landmarks;
        }

        public List<LandmarkDto> getWorldLandmarks() {
            return worldLandmarks;
        }

        public void setWorldLandmarks(List<LandmarkDto> worldLandmarks) {
            this.worldLandmarks = worldLandmarks;
        }
    }

    public static class LandmarkDto {
        private double x;
        private double y;
        private double z;
        private double visibility;
        private double presence;

        public Landmark toDomain() {
            return new Landmark(x, y, z, visibility, presence);
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getZ() {
            return z;
        }

        public void setZ(double z) {
            this.z = z;
        }

        public double getVisibility() {
            return visibility;
        }

        public void setVisibility(double visibility) {
            this.visibility = visibility;
        }

        public double getPresence() {
            return presence;
        }

        public void setPresence(double presence) {
            this.presence = presence;
        }
    }

    public static class FormIssueDto {
        private String code;
        private String severity;
        private String phase;
        private String metricName;
        private double actualValue;
        private String targetRange;
        private long evidenceTimestampMs;
        private String description;

        public FormIssue toDomain() {
            FormIssue issue = new FormIssue();
            issue.setCode(code);
            issue.setSeverity(Severity.valueOf(severity));
            issue.setPhase(phase);
            issue.setMetricName(metricName);
            issue.setActualValue(actualValue);
            issue.setTargetRange(targetRange);
            issue.setEvidenceTimestampMs(evidenceTimestampMs);
            issue.setDescription(description);
            return issue;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getPhase() {
            return phase;
        }

        public void setPhase(String phase) {
            this.phase = phase;
        }

        public String getMetricName() {
            return metricName;
        }

        public void setMetricName(String metricName) {
            this.metricName = metricName;
        }

        public double getActualValue() {
            return actualValue;
        }

        public void setActualValue(double actualValue) {
            this.actualValue = actualValue;
        }

        public String getTargetRange() {
            return targetRange;
        }

        public void setTargetRange(String targetRange) {
            this.targetRange = targetRange;
        }

        public long getEvidenceTimestampMs() {
            return evidenceTimestampMs;
        }

        public void setEvidenceTimestampMs(long evidenceTimestampMs) {
            this.evidenceTimestampMs = evidenceTimestampMs;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class RepAnalysisDto {
        private int repIndex;
        private long startMs;
        private long endMs;
        private double score;
        private List<FormIssueDto> issues = new ArrayList<FormIssueDto>();

        public RepAnalysis toDomain() {
            RepAnalysis rep = new RepAnalysis();
            rep.setRepIndex(repIndex);
            rep.setStartMs(startMs);
            rep.setEndMs(endMs);
            rep.setScore(score);
            List<FormIssue> mapped = new ArrayList<FormIssue>();
            for (FormIssueDto issue : issues) {
                mapped.add(issue.toDomain());
            }
            rep.setIssues(mapped);
            return rep;
        }

        public int getRepIndex() {
            return repIndex;
        }

        public void setRepIndex(int repIndex) {
            this.repIndex = repIndex;
        }

        public long getStartMs() {
            return startMs;
        }

        public void setStartMs(long startMs) {
            this.startMs = startMs;
        }

        public long getEndMs() {
            return endMs;
        }

        public void setEndMs(long endMs) {
            this.endMs = endMs;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public List<FormIssueDto> getIssues() {
            return issues;
        }

        public void setIssues(List<FormIssueDto> issues) {
            this.issues = issues;
        }
    }

    public static class EvidenceFrameDto {
        private String label;
        private long timestampMs;
        private String imageUrl;

        public EvidenceFrame toDomain() {
            EvidenceFrame frame = new EvidenceFrame();
            frame.setLabel(label);
            frame.setTimestampMs(timestampMs);
            frame.setImageUrl(imageUrl);
            return frame;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public long getTimestampMs() {
            return timestampMs;
        }

        public void setTimestampMs(long timestampMs) {
            this.timestampMs = timestampMs;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }
    }

    public static class AdviceDto {
        private String summary;
        private List<String> suggestions = new ArrayList<String>();
        private List<String> warnings = new ArrayList<String>();

        public AdviceBlock toDomain() {
            AdviceBlock block = new AdviceBlock();
            block.setSummary(summary);
            block.setSuggestions(suggestions);
            block.setWarnings(warnings);
            return block;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public List<String> getSuggestions() {
            return suggestions;
        }

        public void setSuggestions(List<String> suggestions) {
            this.suggestions = suggestions;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void setWarnings(List<String> warnings) {
            this.warnings = warnings;
        }
    }
}
