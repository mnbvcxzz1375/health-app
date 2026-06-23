package com.atitai.posture.api;

import com.atitai.posture.service.PostureJobService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/posture/jobs")
public class PostureInternalController {

    private final PostureJobService postureJobService;

    public PostureInternalController(PostureJobService postureJobService) {
        this.postureJobService = postureJobService;
    }

    @PostMapping("/{jobId}/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeJob(@PathVariable("jobId") String jobId, @RequestBody Map<String, Object> payload) {
        postureJobService.acceptResult(jobId, payload);
    }
}
