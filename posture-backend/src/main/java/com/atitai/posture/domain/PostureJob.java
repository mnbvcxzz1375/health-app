package com.atitai.posture.domain;

import java.time.Instant;

public class PostureJob {

    private String id;
    private String userId;
    private ExerciseType exerciseType;
    private CameraView cameraView;
    private JobStatus status;
    private int progress;
    private String failReason;
    private String videoPath;
    private String originalFilename;
    private Instant createdAt;
    private Instant updatedAt;
    private PostureAnalysis analysis;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public ExerciseType getExerciseType() {
        return exerciseType;
    }

    public void setExerciseType(ExerciseType exerciseType) {
        this.exerciseType = exerciseType;
    }

    public CameraView getCameraView() {
        return cameraView;
    }

    public void setCameraView(CameraView cameraView) {
        this.cameraView = cameraView;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public PostureAnalysis getAnalysis() {
        return analysis;
    }

    public void setAnalysis(PostureAnalysis analysis) {
        this.analysis = analysis;
    }
}

