package com.atitai.posture.adapter.inference;

import com.atitai.posture.domain.PoseInferenceRequest;

public class InferenceAnalyzeRequest {

    private String jobId;
    private String videoPath;
    private String evidenceOutputDir;
    private String exerciseType;
    private String cameraView;

    public static InferenceAnalyzeRequest from(PoseInferenceRequest request) {
        InferenceAnalyzeRequest payload = new InferenceAnalyzeRequest();
        payload.setJobId(request.getJobId());
        payload.setVideoPath(request.getVideoPath());
        payload.setEvidenceOutputDir(request.getEvidenceOutputDir());
        payload.setExerciseType(request.getExerciseType().name());
        payload.setCameraView(request.getCameraView().name());
        return payload;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public String getEvidenceOutputDir() {
        return evidenceOutputDir;
    }

    public void setEvidenceOutputDir(String evidenceOutputDir) {
        this.evidenceOutputDir = evidenceOutputDir;
    }

    public String getExerciseType() {
        return exerciseType;
    }

    public void setExerciseType(String exerciseType) {
        this.exerciseType = exerciseType;
    }

    public String getCameraView() {
        return cameraView;
    }

    public void setCameraView(String cameraView) {
        this.cameraView = cameraView;
    }
}
