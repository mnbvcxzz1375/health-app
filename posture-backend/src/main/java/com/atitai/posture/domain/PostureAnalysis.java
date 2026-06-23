package com.atitai.posture.domain;

import java.util.ArrayList;
import java.util.List;

public class PostureAnalysis {

    private ExerciseType exerciseType;
    private double score;
    private Verdict verdict;
    private double validFrameRatio;
    private String summary;
    private List<FormIssue> issues = new ArrayList<FormIssue>();
    private List<String> suggestions = new ArrayList<String>();
    private List<String> warnings = new ArrayList<String>();
    private List<RepAnalysis> reps = new ArrayList<RepAnalysis>();
    private List<EvidenceFrame> evidenceFrames = new ArrayList<EvidenceFrame>();

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

    public double getValidFrameRatio() {
        return validFrameRatio;
    }

    public void setValidFrameRatio(double validFrameRatio) {
        this.validFrameRatio = validFrameRatio;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<FormIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<FormIssue> issues) {
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
}

