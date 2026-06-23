package com.atitai.posture.adapter.advisor;

import com.atitai.posture.config.PostureProperties;
import com.atitai.posture.domain.AdviceBlock;
import com.atitai.posture.domain.FormIssue;
import com.atitai.posture.domain.PostureAnalysis;
import com.atitai.posture.domain.PostureJob;
import com.atitai.posture.port.CorrectionAdvisor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Component
@Primary
public class ResilientCorrectionAdvisor implements CorrectionAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ResilientCorrectionAdvisor.class);

    private final PostureProperties properties;
    private final RestTemplate llmRestTemplate;
    private final TemplateCorrectionAdvisor fallbackAdvisor;
    private final ObjectMapper objectMapper;

    public ResilientCorrectionAdvisor(PostureProperties properties,
        @Qualifier("llmRestTemplate") RestTemplate llmRestTemplate,
        TemplateCorrectionAdvisor fallbackAdvisor,
        ObjectMapper objectMapper) {
        this.properties = properties;
        this.llmRestTemplate = llmRestTemplate;
        this.fallbackAdvisor = fallbackAdvisor;
        this.objectMapper = objectMapper;
    }

    @Override
    public AdviceBlock advise(PostureJob job, PostureAnalysis analysis) {
        if (!properties.getLlm().isEnabled() || !StringUtils.hasText(properties.getLlm().getApiKey())) {
            return fallbackAdvisor.advise(job, analysis);
        }

        try {
            return requestAdvice(job, analysis);
        } catch (Exception ex) {
            log.warn("LLM advice failed, falling back to template advisor: {}", ex.getMessage());
            return fallbackAdvisor.advise(job, analysis);
        }
    }

    private AdviceBlock requestAdvice(PostureJob job, PostureAnalysis analysis) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getLlm().getApiKey());

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("model", properties.getLlm().getModel());
        payload.put("response_format", singletonObjectMap("type", "json_object"));
        payload.put("messages", buildMessages(job, analysis));

        JsonNode response = llmRestTemplate.postForObject(
            properties.getLlm().getBaseUrl() + "/chat/completions",
            new HttpEntity<Map<String, Object>>(payload, headers),
            JsonNode.class
        );
        if (response == null) {
            throw new IllegalStateException("LLM response body is empty");
        }

        JsonNode contentNode = response.at("/choices/0/message/content");
        if (contentNode.isMissingNode() || !contentNode.isTextual()) {
            throw new IllegalStateException("LLM response content is missing");
        }

        Map<String, Object> decoded = objectMapper.readValue(
            contentNode.asText(),
            new TypeReference<Map<String, Object>>() {
            }
        );

        AdviceBlock block = new AdviceBlock();
        block.setSummary(asString(decoded.get("summary")));
        block.setSuggestions(asStringList(decoded.get("topSuggestions")));
        block.setWarnings(asStringList(decoded.get("riskWarnings")));
        if (!StringUtils.hasText(block.getSummary()) || block.getSuggestions().isEmpty()) {
            throw new IllegalStateException("LLM response does not match required schema");
        }
        return block;
    }

    private List<Map<String, String>> buildMessages(PostureJob job, PostureAnalysis analysis) throws Exception {
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        messages.add(stringMap(
            "role", "system",
            "content", "你是健身动作纠正助手。只基于提供的结构化分析结果给建议，不要编造视频内容。输出 JSON，字段必须是 summary、topSuggestions、riskWarnings。"
        ));

        Map<String, Object> userPayload = new HashMap<String, Object>();
        userPayload.put("exerciseType", job.getExerciseType().name());
        userPayload.put("cameraView", job.getCameraView().name());
        userPayload.put("score", analysis.getScore());
        userPayload.put("verdict", analysis.getVerdict().name());
        userPayload.put("validFrameRatio", analysis.getValidFrameRatio());
        userPayload.put("issues", buildIssuePayload(analysis.getIssues()));
        userPayload.put("evidenceFrames", analysis.getEvidenceFrames());

        messages.add(stringMap("role", "user", "content", objectMapper.writeValueAsString(userPayload)));
        return messages;
    }

    private List<Map<String, Object>> buildIssuePayload(List<FormIssue> issues) {
        List<Map<String, Object>> payload = new ArrayList<Map<String, Object>>();
        for (FormIssue issue : issues) {
            Map<String, Object> entry = new HashMap<String, Object>();
            entry.put("code", issue.getCode());
            entry.put("severity", issue.getSeverity().name());
            entry.put("phase", issue.getPhase());
            entry.put("metricName", issue.getMetricName());
            entry.put("actualValue", issue.getActualValue());
            entry.put("targetRange", issue.getTargetRange());
            entry.put("evidenceTimestampMs", issue.getEvidenceTimestampMs());
            payload.add(entry);
        }
        return payload;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        List<String> result = new ArrayList<String>();
        if (value instanceof List) {
            for (Object item : (List<Object>) value) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private Map<String, Object> singletonObjectMap(String key, Object value) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(key, value);
        return map;
    }

    private Map<String, String> stringMap(String key1, String value1, String key2, String value2) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }
}
