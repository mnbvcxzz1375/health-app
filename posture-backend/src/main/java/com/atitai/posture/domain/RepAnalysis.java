package com.atitai.posture.domain;

import java.util.ArrayList;
import java.util.List;

public class RepAnalysis {

    private int repIndex;
    private long startMs;
    private long endMs;
    private double score;
    private List<FormIssue> issues = new ArrayList<FormIssue>();

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

    public List<FormIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<FormIssue> issues) {
        this.issues = issues;
    }
}

