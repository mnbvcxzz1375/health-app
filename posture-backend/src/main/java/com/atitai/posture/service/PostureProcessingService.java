package com.atitai.posture.service;

import com.atitai.posture.config.PostureProperties;
import com.atitai.posture.domain.JobStatus;
import com.atitai.posture.domain.PostureAnalysis;
import com.atitai.posture.domain.PostureJob;
import com.atitai.posture.port.PostureJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PostureProcessingService {

    private final PostureJobRepository postureJobRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final PostureProperties postureProperties;
    private final ObjectMapper objectMapper;

    public PostureProcessingService(
        PostureJobRepository postureJobRepository,
        StringRedisTemplate stringRedisTemplate,
        PostureProperties postureProperties,
        ObjectMapper objectMapper
    ) {
        this.postureJobRepository = postureJobRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.postureProperties = postureProperties;
        this.objectMapper = objectMapper;
    }

    public void enqueue(PostureJob job, String evidenceOutputDir) {
        try {
            QueueMessage message = new QueueMessage();
            message.setJobId(job.getId());
            message.setVideoPath(job.getVideoPath());
            message.setEvidenceOutputDir(evidenceOutputDir);
            message.setExerciseType(job.getExerciseType() == null ? null : job.getExerciseType().name());
            message.setCameraView(job.getCameraView() == null ? null : job.getCameraView().name());

            job.setStatus(JobStatus.PENDING);
            job.setProgress(5);
            job.setUpdatedAt(Instant.now());
            postureJobRepository.save(job);

            String payload = objectMapper.writeValueAsString(message);
            stringRedisTemplate.opsForList().leftPush(postureProperties.getQueue().getRedisKey(), payload);
        } catch (Exception exception) {
            job.setStatus(JobStatus.FAILED);
            job.setProgress(100);
            job.setFailReason(exception.getMessage());
            job.setUpdatedAt(Instant.now());
            postureJobRepository.save(job);
            throw new IllegalStateException("Failed to enqueue posture job: " + exception.getMessage(), exception);
        }
    }

    public void complete(String jobId, Map<String, Object> payload) {
        PostureJob job = postureJobRepository.findById(jobId)
            .orElseThrow(new java.util.function.Supplier<NoSuchElementException>() {
                @Override
                public NoSuchElementException get() {
                    return new NoSuchElementException("Posture job not found: " + jobId);
                }
            });

        String statusValue = payload.get("status") == null ? "FAILED" : String.valueOf(payload.get("status"));
        JobStatus status = JobStatus.valueOf(statusValue);
        job.setStatus(status);
        job.setProgress(payload.get("progress") instanceof Number ? ((Number) payload.get("progress")).intValue() : 100);
        job.setFailReason(payload.get("failReason") == null ? null : String.valueOf(payload.get("failReason")));
        if (payload.get("analysis") != null) {
            job.setAnalysis(objectMapper.convertValue(payload.get("analysis"), PostureAnalysis.class));
        }
        job.setUpdatedAt(Instant.now());
        postureJobRepository.save(job);
    }

    public static class QueueMessage {
        private String jobId;
        private String videoPath;
        private String evidenceOutputDir;
        private String exerciseType;
        private String cameraView;

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
}
