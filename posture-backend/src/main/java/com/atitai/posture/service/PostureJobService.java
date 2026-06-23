package com.atitai.posture.service;

import com.atitai.posture.domain.CameraView;
import com.atitai.posture.domain.ExerciseType;
import com.atitai.posture.domain.JobStatus;
import com.atitai.posture.domain.PostureAnalysis;
import com.atitai.posture.domain.PostureJob;
import com.atitai.posture.domain.StoredVideo;
import com.atitai.posture.port.PostureJobRepository;
import com.atitai.posture.port.VideoStoragePort;
import java.io.IOException;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PostureJobService {

    private final VideoStoragePort videoStoragePort;
    private final PostureJobRepository postureJobRepository;
    private final PostureProcessingService postureProcessingService;

    public PostureJobService(VideoStoragePort videoStoragePort, PostureJobRepository postureJobRepository,
        PostureProcessingService postureProcessingService) {
        this.videoStoragePort = videoStoragePort;
        this.postureJobRepository = postureJobRepository;
        this.postureProcessingService = postureProcessingService;
    }

    public PostureJob createJob(String userId, String exerciseTypeValue, String cameraViewValue, MultipartFile videoFile)
        throws IOException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId is required");
        }

        ExerciseType exerciseType = ExerciseType.fromValue(exerciseTypeValue);
        CameraView cameraView = CameraView.fromValue(cameraViewValue);
        String jobId = UUID.randomUUID().toString();
        StoredVideo storedVideo = videoStoragePort.store(jobId, videoFile);

        PostureJob job = new PostureJob();
        job.setId(jobId);
        job.setUserId(userId.trim());
        job.setExerciseType(exerciseType);
        job.setCameraView(cameraView);
        job.setStatus(JobStatus.PENDING);
        job.setProgress(0);
        job.setVideoPath(storedVideo.getAbsolutePath());
        job.setOriginalFilename(storedVideo.getOriginalFilename());
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        postureJobRepository.save(job);

        postureProcessingService.enqueue(job, storedVideo.getEvidenceDirectory());
        return job;
    }

    public PostureJob getJob(String jobId) {
        return postureJobRepository.findById(jobId)
            .orElseThrow(new java.util.function.Supplier<NoSuchElementException>() {
                @Override
                public NoSuchElementException get() {
                    return new NoSuchElementException("Posture job not found: " + jobId);
                }
            });
    }

    public PostureAnalysis getReport(String jobId) {
        PostureJob job = getJob(jobId);
        if (job.getAnalysis() == null) {
            throw new IllegalStateException("Report is not available yet");
        }
        return job.getAnalysis();
    }

    public void acceptResult(String jobId, java.util.Map<String, Object> payload) {
        postureProcessingService.complete(jobId, payload);
    }
}
