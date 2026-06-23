package com.atitai.posture.domain;

public class FormIssue {

    private String code;
    private Severity severity;
    private String phase;
    private String metricName;
    private double actualValue;
    private String targetRange;
    private long evidenceTimestampMs;
    private String description;

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

