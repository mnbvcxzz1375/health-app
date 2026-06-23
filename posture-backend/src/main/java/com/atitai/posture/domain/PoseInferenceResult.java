package com.atitai.posture.domain;

import java.util.ArrayList;
import java.util.List;

public class PoseInferenceResult {

    private List<PoseFrame> frames = new ArrayList<PoseFrame>();
    private PostureAnalysis preliminaryAnalysis;
    private String provider;
    private String failReason;

    public List<PoseFrame> getFrames() {
        return frames;
    }

    public void setFrames(List<PoseFrame> frames) {
        this.frames = frames;
    }

    public PostureAnalysis getPreliminaryAnalysis() {
        return preliminaryAnalysis;
    }

    public void setPreliminaryAnalysis(PostureAnalysis preliminaryAnalysis) {
        this.preliminaryAnalysis = preliminaryAnalysis;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }
}

