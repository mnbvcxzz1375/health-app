package com.atitai.posture.adapter.inference;

import com.atitai.posture.config.PostureProperties;
import com.atitai.posture.domain.PoseInferenceRequest;
import com.atitai.posture.domain.PoseInferenceResult;
import com.atitai.posture.port.PoseInferenceProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpPoseInferenceProvider implements PoseInferenceProvider {

    private final RestTemplate restTemplate;
    private final PostureProperties properties;

    public HttpPoseInferenceProvider(@Qualifier("inferenceRestTemplate") RestTemplate restTemplate,
        PostureProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public PoseInferenceResult analyze(PoseInferenceRequest request) {
        String endpoint = properties.getInference().getBaseUrl() + "/internal/v1/pose/analyze";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<InferenceAnalyzeRequest> entity = new HttpEntity<InferenceAnalyzeRequest>(
            InferenceAnalyzeRequest.from(request),
            headers
        );
        try {
            InferenceAnalyzeResponse response =
                restTemplate.postForObject(endpoint, entity, InferenceAnalyzeResponse.class);
            if (response == null) {
                throw new IllegalStateException("Pose inference service returned no body");
            }
            return response.toDomain();
        } catch (RestClientException ex) {
            throw new IllegalStateException("Failed to call pose inference service: " + ex.getMessage(), ex);
        }
    }
}
