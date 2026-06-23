package com.atitai.posture.api.dto;

import com.atitai.posture.domain.EvidenceFrame;
import com.atitai.posture.domain.ExerciseType;
import com.atitai.posture.domain.FormIssue;
import com.atitai.posture.domain.PostureAnalysis;
import com.atitai.posture.domain.RepAnalysis;
import com.atitai.posture.domain.Severity;
import com.atitai.posture.domain.Verdict;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class PostureReportResponse {

    private ExerciseType exerciseType;
    private double score;
    private Verdict verdict;
    private String summary;
    private List<IssueView> issues = new ArrayList<IssueView>();
    private List<String> suggestions = new ArrayList<String>();
    private List<String> warnings = new ArrayList<String>();
    private List<RepAnalysis> reps = new ArrayList<RepAnalysis>();
    private List<EvidenceFrame> evidenceFrames = new ArrayList<EvidenceFrame>();
    private double validFrameRatio;

    public static PostureReportResponse from(PostureAnalysis analysis, String baseUrl) {
        PostureReportResponse response = new PostureReportResponse();
        response.setExerciseType(analysis.getExerciseType());
        response.setScore(analysis.getScore());
        response.setVerdict(analysis.getVerdict());
        response.setSummary(analysis.getSummary());
        response.setSuggestions(analysis.getSuggestions());
        response.setWarnings(analysis.getWarnings());
        response.setReps(analysis.getReps());
        response.setValidFrameRatio(analysis.getValidFrameRatio());

        List<IssueView> mappedIssues = new ArrayList<IssueView>();
        for (FormIssue issue : analysis.getIssues()) {
            mappedIssues.add(IssueView.from(issue));
        }
        response.setIssues(mappedIssues);

        List<EvidenceFrame> mappedFrames = new ArrayList<EvidenceFrame>();
        for (EvidenceFrame frame : analysis.getEvidenceFrames()) {
            EvidenceFrame mapped = new EvidenceFrame();
            mapped.setLabel(frame.getLabel());
            mapped.setTimestampMs(frame.getTimestampMs());
            mapped.setImageUrl(resolveUrl(baseUrl, frame.getImageUrl()));
            mappedFrames.add(mapped);
        }
        response.setEvidenceFrames(mappedFrames);
        return response;
    }

    private static String resolveUrl(String baseUrl, String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl;
        }
        if (imageUrl.startsWith("/")) {
            return baseUrl + imageUrl;
        }
        return baseUrl + "/api/v1/posture/storage/" + imageUrl;
    }

    public ExerciseType getExerciseType() {
        return exerciseType;
    }

    public void setExerciseType(ExerciseType exerciseType) {
        this.exerciseType = exerciseType;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public void setVerdict(Verdict verdict) {
        this.verdict = verdict;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<IssueView> getIssues() {
        return issues;
    }

    public void setIssues(List<IssueView> issues) {
        this.issues = issues;
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

    public List<RepAnalysis> getReps() {
        return reps;
    }

    public void setReps(List<RepAnalysis> reps) {
        this.reps = reps;
    }

    public List<EvidenceFrame> getEvidenceFrames() {
        return evidenceFrames;
    }

    public void setEvidenceFrames(List<EvidenceFrame> evidenceFrames) {
        this.evidenceFrames = evidenceFrames;
    }

    public double getValidFrameRatio() {
        return validFrameRatio;
    }

    public void setValidFrameRatio(double validFrameRatio) {
        this.validFrameRatio = validFrameRatio;
    }

    public static class IssueView {
        private String code;
        private Severity severity;
        private String phase;
        private String metricName;
        private double actualValue;
        private String targetRange;
        private long evidenceTimestampMs;
        private String description;

        public static IssueView from(FormIssue issue) {
            IssueView view = new IssueView();
            view.setCode(issue.getCode());
            view.setSeverity(issue.getSeverity());
            view.setPhase(issue.getPhase());
            view.setMetricName(issue.getMetricName());
            view.setActualValue(issue.getActualValue());
            view.setTargetRange(issue.getTargetRange());
            view.setEvidenceTimestampMs(issue.getEvidenceTimestampMs());
            view.setDescription(issue.getDescription());
            return view;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public Severity getSeverity() {
            return severity;
        }

        public void setSeverity(Severity severity) {
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
}
