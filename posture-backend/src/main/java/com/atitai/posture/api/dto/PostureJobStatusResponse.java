package com.atitai.posture.api.dto;

import com.atitai.posture.domain.JobStatus;
import com.atitai.posture.domain.PostureJob;

public class PostureJobStatusResponse {

    private String jobId;
    private JobStatus status;
    private int progress;
    private String failReason;

    public static PostureJobStatusResponse from(PostureJob job) {
        PostureJobStatusResponse response = new PostureJobStatusResponse();
        response.setJobId(job.getId());
        response.setStatus(job.getStatus());
        response.setProgress(job.getProgress());
        response.setFailReason(job.getFailReason());
        return response;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
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
}
