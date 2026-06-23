package com.atitai.posture.api;

import com.atitai.posture.api.dto.CreatePostureJobResponse;
import com.atitai.posture.api.dto.PostureJobStatusResponse;
import com.atitai.posture.api.dto.PostureReportResponse;
import com.atitai.posture.domain.PostureAnalysis;
import com.atitai.posture.domain.PostureJob;
import com.atitai.posture.service.PostureJobService;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/posture/jobs")
public class PostureJobController {

    private final PostureJobService postureJobService;

    public PostureJobController(PostureJobService postureJobService) {
        this.postureJobService = postureJobService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreatePostureJobResponse> createJob(
        @RequestParam("userId") String userId,
        @RequestParam("exerciseType") String exerciseType,
        @RequestParam("cameraView") String cameraView,
        @RequestPart("videoFile") MultipartFile videoFile
    ) throws IOException {
        PostureJob job = postureJobService.createJob(userId, exerciseType, cameraView, videoFile);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(CreatePostureJobResponse.of(job.getId(), job.getStatus()));
    }

    @GetMapping("/{jobId}")
    public PostureJobStatusResponse getJobStatus(@PathVariable("jobId") String jobId) {
        return PostureJobStatusResponse.from(postureJobService.getJob(jobId));
    }

    @GetMapping("/{jobId}/report")
    public PostureReportResponse getReport(@PathVariable("jobId") String jobId) {
        PostureAnalysis analysis = postureJobService.getReport(jobId);
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return PostureReportResponse.from(analysis, baseUrl);
    }
}
