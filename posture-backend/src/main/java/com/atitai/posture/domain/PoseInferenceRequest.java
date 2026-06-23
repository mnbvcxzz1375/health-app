package com.atitai.posture.domain;

public class PoseInferenceRequest {

    private final String jobId;
    private final String videoPath;
    private final String evidenceOutputDir;
    private final ExerciseType exerciseType;
    private final CameraView cameraView;

    public PoseInferenceRequest(String jobId, String videoPath, String evidenceOutputDir, ExerciseType exerciseType,
        CameraView cameraView) {
        this.jobId = jobId;
        this.videoPath = videoPath;
        this.evidenceOutputDir = evidenceOutputDir;
        this.exerciseType = exerciseType;
        this.cameraView = cameraView;
    }

    public String getJobId() {
        return jobId;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public String getEvidenceOutputDir() {
        return evidenceOutputDir;
    }

    public ExerciseType getExerciseType() {
        return exerciseType;
    }

    public CameraView getCameraView() {
        return cameraView;
    }
}

