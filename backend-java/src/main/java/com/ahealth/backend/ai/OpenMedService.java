package com.ahealth.backend.ai;

import com.ahealth.backend.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OpenMedService {
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final String apiToken;
  private final String nerModel;
  private final String piiModel;
  // 蒸馏后 student 模型名（仅作为元信息，实际切换由本地推理服务的环境变量 OPENMED_PII_USE_TEACHER 控制）
  private final String piiStudentModel;
  private final String localInferenceUrl;

  public OpenMedService(
      ObjectMapper objectMapper,
      @Value("${OPENMED_API_TOKEN:}") String apiToken,
      @Value("${OPENMED_NER_MODEL:OpenMed/OpenMed-NER-PharmaDetect-SuperMedical-125M}") String nerModel,
      @Value("${OPENMED_PII_MODEL:OpenMed/OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1}") String piiModel,
      @Value("${OPENMED_PII_STUDENT_MODEL:OpenMed-PII-DistilBERT-chinese}") String piiStudentModel,
      @Value("${OPENMED_LOCAL_URL:http://127.0.0.1:8012}") String localInferenceUrl
  ) {
    this.objectMapper = objectMapper;
    this.apiToken = apiToken == null ? "" : apiToken.trim();
    this.nerModel = nerModel;
    this.piiModel = piiModel;
    this.piiStudentModel = piiStudentModel;
    this.localInferenceUrl = localInferenceUrl.endsWith("/") ? localInferenceUrl.substring(0, localInferenceUrl.length() - 1) : localInferenceUrl;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }

  /**
   * Check if local inference service is available.
   */
  private boolean isLocalAvailable() {
    try {
      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(localInferenceUrl + "/health"))
          .timeout(Duration.ofSeconds(2))
          .GET()
          .build();
      HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return resp.statusCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Call PharmaDetect NER to extract medical entities from text.
   * Tries local inference first, falls back to HuggingFace API.
   */
  public List<AiDtos.NerEntity> nerExtract(String text) {
    if (text == null || text.isBlank()) return List.of();

    // Try local inference first
    try {
      String url = localInferenceUrl + "/ner/extract";
      Map<String, String> body = Map.of("text", text);
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(30))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() == 200) {
        JsonNode result = objectMapper.readTree(response.body());
        return parseNerResultFromLocal(result);
      }
    } catch (Exception ignored) {
      // Local inference failed, try HuggingFace API
    }

    // Fallback to HuggingFace API
    JsonNode result = callHfInference(nerModel, text, "ner");
    return parseNerResult(result, text);
  }

  /**
   * Call PII detection model to find personal information in text.
   * Tries local inference first, falls back to HuggingFace API.
   */
  public List<AiDtos.PiiMask> piiDetect(String text) {
    if (text == null || text.isBlank()) return List.of();

    // Try local inference first
    try {
      String url = localInferenceUrl + "/pii/detect";
      Map<String, String> body = Map.of("text", text);
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(30))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() == 200) {
        JsonNode result = objectMapper.readTree(response.body());
        return parsePiiResultFromLocal(result);
      }
    } catch (Exception ignored) {
      // Local inference failed, try HuggingFace API
    }

    // Fallback to HuggingFace API
    JsonNode result = callHfInference(piiModel, text, "ner");
    return parsePiiResult(result, text);
  }

  /**
   * Call HuggingFace Inference API for a given model.
   * Supports NER (token-classification) and VQA (visual-question-answering) tasks.
   */
  private JsonNode callHfInference(String model, String input, String taskType) {
    if (apiToken.isBlank()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
          "本地推理服务不可用且未配置 OPENMED_API_TOKEN，无法调用 OpenMed 模型。");
    }

    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("inputs", input);
      if (taskType.equals("ner")) {
        body.put("parameters", Map.of("aggregation_strategy", "simple"));
      }

      String url = "https://api-inference.huggingface.co/models/" + model;
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(60))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + apiToken)
          .POST(HttpRequest.BodyPublishers.ofString(
              objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
          .build();

      HttpResponse<String> response = httpClient.send(
          request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ApiException(HttpStatus.BAD_GATEWAY,
            "OpenMed 模型调用失败，状态码：" + response.statusCode());
      }
      return objectMapper.readTree(response.body());
    } catch (ApiException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.BAD_GATEWAY, "OpenMed 调用被中断：" + e.getMessage());
    } catch (IOException e) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "OpenMed 调用失败：" + e.getMessage());
    }
  }

  private List<AiDtos.NerEntity> parseNerResult(JsonNode result, String originalText) {
    List<AiDtos.NerEntity> entities = new ArrayList<>();
    if (!result.isArray()) return entities;
    for (JsonNode item : result) {
      String word = item.path("word").asText("");
      String entityGroup = item.path("entity_group").asText(item.path("entity").asText(""));
      double score = item.path("score").asDouble(0);
      int start = item.path("start").asInt(0);
      int end = item.path("end").asInt(0);
      if (!word.isBlank() && score > 0.5) {
        entities.add(new AiDtos.NerEntity(word, entityGroup, start, end, score));
      }
    }
    return entities;
  }

  private List<AiDtos.PiiMask> parsePiiResult(JsonNode result, String originalText) {
    List<AiDtos.PiiMask> masks = new ArrayList<>();
    if (!result.isArray()) return masks;
    int counter = 0;
    Map<String, Integer> typeCounters = new HashMap<>();
    for (JsonNode item : result) {
      String word = item.path("word").asText("");
      String entityGroup = item.path("entity_group").asText(item.path("entity").asText(""));
      int start = item.path("start").asInt(0);
      int end = item.path("end").asInt(0);
      if (!word.isBlank()) {
        int count = typeCounters.merge(entityGroup, 1, Integer::sum);
        String maskToken = "[" + entityGroup + "_" + count + "]";
        masks.add(new AiDtos.PiiMask(word, maskToken, entityGroup, start, end));
      }
    }
    return masks;
  }

  /** Parse NER results from local inference service format: {entities: [{text, label, score, start, end}]} */
  private List<AiDtos.NerEntity> parseNerResultFromLocal(JsonNode result) {
    List<AiDtos.NerEntity> entities = new ArrayList<>();
    JsonNode entitiesNode = result.path("entities");
    if (!entitiesNode.isArray()) return entities;
    for (JsonNode item : entitiesNode) {
      String word = item.path("text").asText("");
      String label = item.path("label").asText("");
      double score = item.path("score").asDouble(0);
      int start = item.path("start").asInt(0);
      int end = item.path("end").asInt(0);
      if (!word.isBlank() && score > 0.3) {
        entities.add(new AiDtos.NerEntity(word, label, start, end, score));
      }
    }
    return entities;
  }

  /** Parse PII results from local inference service format: {masks: [{text, label, score, start, end}]} */
  private List<AiDtos.PiiMask> parsePiiResultFromLocal(JsonNode result) {
    List<AiDtos.PiiMask> masks = new ArrayList<>();
    JsonNode masksNode = result.path("masks");
    if (!masksNode.isArray()) return masks;
    Map<String, Integer> typeCounters = new HashMap<>();
    for (JsonNode item : masksNode) {
      String word = item.path("text").asText("");
      String label = item.path("label").asText("");
      double score = item.path("score").asDouble(0);
      int start = item.path("start").asInt(0);
      int end = item.path("end").asInt(0);
      if (!word.isBlank() && score > 0.5) {
        int count = typeCounters.merge(label, 1, Integer::sum);
        String maskToken = "[" + label + "_" + count + "]";
        masks.add(new AiDtos.PiiMask(word, maskToken, label, start, end));
      }
    }
    return masks;
  }

  public boolean isConfigured() {
    return !apiToken.isBlank() || !localInferenceUrl.isBlank();
  }
}
