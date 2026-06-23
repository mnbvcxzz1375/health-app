package com.atitai.posture.domain;

import java.util.ArrayList;
import java.util.List;

public class PoseFrame {

    private long timestampMs;
    private List<Landmark> landmarks = new ArrayList<Landmark>();
    private List<Landmark> worldLandmarks = new ArrayList<Landmark>();
    private double trackingConfidence;

    public long getTimestampMs() {
        return timestampMs;
    }

    public void setTimestampMs(long timestampMs) {
        this.timestampMs = timestampMs;
    }

    public List<Landmark> getLandmarks() {
        return landmarks;
    }

    public void setLandmarks(List<Landmark> landmarks) {
        this.landmarks = landmarks;
    }

    public List<Landmark> getWorldLandmarks() {
        return worldLandmarks;
    }

    public void setWorldLandmarks(List<Landmark> worldLandmarks) {
        this.worldLandmarks = worldLandmarks;
    }

    public double getTrackingConfidence() {
        return trackingConfidence;
    }

    public void setTrackingConfidence(double trackingConfidence) {
        this.trackingConfidence = trackingConfidence;
    }
}

