package com.atitai.posture.api.dto;

import com.atitai.posture.domain.JobStatus;

public class CreatePostureJobResponse {

    private String jobId;
    private JobStatus status;

    public static CreatePostureJobResponse of(String jobId, JobStatus status) {
        CreatePostureJobResponse response = new CreatePostureJobResponse();
        response.setJobId(jobId);
        response.setStatus(status);
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
}
