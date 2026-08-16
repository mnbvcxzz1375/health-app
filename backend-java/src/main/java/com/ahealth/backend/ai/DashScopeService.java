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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DashScopeService {
  private static final Logger log = LoggerFactory.getLogger(DashScopeService.class);

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final String apiKey;
  private final String baseUrl;
  private final String visionModel;
  private final String chatModel;
  private final LlmCacheService llmCacheService;
  private final int cacheTtlHours;

  public DashScopeService(
      ObjectMapper objectMapper,
      @Value("${DASHSCOPE_API_KEY:${QWEN_API_KEY:}}") String apiKey,
      @Value("${DASHSCOPE_BASE_URL:https://coding.dashscope.aliyuncs.com/v1}") String baseUrl,
      @Value("${DASHSCOPE_VISION_MODEL:kimi-k2.5}") String visionModel,
      @Value("${DASHSCOPE_CHAT_MODEL:kimi-k2.5}") String chatModel,
      @Lazy LlmCacheService llmCacheService,
      @Value("${rag.cache.llm-ttl-hours:168}") int cacheTtlHours
  ) {
    this.objectMapper = objectMapper;
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.baseUrl = (baseUrl == null || baseUrl.isBlank())
        ? "https://coding.dashscope.aliyuncs.com/v1"
        : baseUrl.trim();
    this.visionModel = visionModel == null || visionModel.isBlank() ? "kimi-k2.5" : visionModel.trim();
    this.chatModel = chatModel == null || chatModel.isBlank() ? "kimi-k2.5" : chatModel.trim();
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
    this.llmCacheService = llmCacheService;
    this.cacheTtlHours = cacheTtlHours;
  }

  public String chatModel() {
    return chatModel;
  }

  public String visionModel() {
    return visionModel;
  }

  public JsonNode requestJson(String systemPrompt, Object userContent, String model, double temperature, String featureName) {
    JsonNode response = requestChatCompletion(systemPrompt, userContent, model, temperature, false, featureName);
    String text = extractAssistantText(response.path("choices").path(0).path("message").path("content"));
    JsonNode parsed = extractJsonObject(text);
    if (parsed == null || parsed.isMissingNode()) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, featureName + "返回格式无效，未得到可解析的 JSON。");
    }
    return parsed;
  }

  /**
   * 带缓存的 JSON 请求：先查精确缓存，再查语义缓存（低温度场景），最后调 LLM 并写缓存。
   *
   * @param scene        场景标识（consult / explain_medication / rehab_plan_draft ...）
   * @param systemPrompt 系统提示词
   * @param userContent  用户内容（String 或 List/Map）
   * @param model        模型名
   * @param temperature  温度（&lt;0.4 启用语义缓存）
   * @param contextHash  上下文哈希（用于区分不同用户上下文的相同 prompt）
   * @return LLM 返回的 JSON
   */
  public JsonNode requestJsonWithCache(
      String scene, String systemPrompt, Object userContent,
      String model, double temperature, String contextHash
  ) {
    String promptKey = systemPrompt + "|" + serialize(userContent);

    // 1. 精确缓存
    Optional<String> exact = llmCacheService.getExact(scene, promptKey, contextHash);
    if (exact.isPresent()) {
      log.debug("[DashScope] 缓存命中 scene={}", scene);
      JsonNode parsed = extractJsonObject(exact.get());
      if (parsed != null && !parsed.isMissingNode()) {
        return parsed;
      }
    }

    // 2. 语义缓存（仅低温度场景）
    if (temperature < 0.4) {
      Optional<String> semantic = llmCacheService.getSemantic(scene, promptKey);
      if (semantic.isPresent()) {
        log.debug("[DashScope] 语义缓存命中 scene={}", scene);
        JsonNode parsed = extractJsonObject(semantic.get());
        if (parsed != null && !parsed.isMissingNode()) {
          return parsed;
        }
      }
    }

    // 3. 调 LLM
    JsonNode response = requestChatCompletion(systemPrompt, userContent, model, temperature, false, "缓存场景:" + scene);
    String text = extractAssistantText(response.path("choices").path(0).path("message").path("content"));
    JsonNode parsed = extractJsonObject(text);
    if (parsed == null || parsed.isMissingNode()) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "缓存场景:" + scene + " 返回格式无效，未得到可解析的 JSON。");
    }

    // 4. 写缓存
    try {
      llmCacheService.put(
          scene, promptKey, contextHash, text,
          Duration.ofHours(cacheTtlHours), temperature < 0.4);
    } catch (Exception e) {
      log.warn("[DashScope] 写缓存失败 scene={}: {}", scene, e.getMessage());
    }

    return parsed;
  }

  /** 序列化 userContent 用于构造 promptKey。String 直返；List/Map 用 ObjectMapper。 */
  private String serialize(Object userContent) {
    if (userContent == null) return "";
    if (userContent instanceof String s) return s;
    if (userContent instanceof List<?> || userContent instanceof Map<?, ?>) {
      try {
        return objectMapper.writeValueAsString(userContent);
      } catch (Exception e) {
        return String.valueOf(userContent);
      }
    }
    return String.valueOf(userContent);
  }

  public String requestText(String systemPrompt, Object userContent, String model, double temperature, String featureName) {
    JsonNode response = requestChatCompletion(systemPrompt, userContent, model, temperature, false, featureName);
    String text = extractAssistantText(response.path("choices").path(0).path("message").path("content")).trim();
    if (text.isBlank()) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, featureName + "返回内容为空。");
    }
    return text;
  }

  public JsonNode requestChatCompletion(
      String systemPrompt,
      Object userContent,
      String model,
      double temperature,
      boolean stream,
      String featureName
  ) {
    ensureConfigured(featureName);

    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", model);
      body.put("messages", List.of(
          Map.of("role", "system", "content", systemPrompt),
          Map.of("role", "user", "content", userContent)
      ));
      body.put("temperature", temperature);
      if (stream) {
        body.put("stream", true);
      }

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/chat/completions"))
          .timeout(Duration.ofSeconds(180))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + apiKey)
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ApiException(HttpStatus.valueOf(response.statusCode()), readErrorMessage(response.body(), featureName + "调用失败"));
      }
      return objectMapper.readTree(response.body());
    } catch (ApiException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.BAD_GATEWAY, featureName + "调用失败：" + exception.getMessage());
    } catch (IOException exception) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, featureName + "调用失败：" + exception.getMessage());
    }
  }

  public List<Map<String, Object>> toImageBlocks(MultipartFile[] files) {
    try {
      return java.util.Arrays.stream(files == null ? new MultipartFile[0] : files)
          .filter(file -> file != null && !file.isEmpty())
          .map(file -> {
            try {
              String mimeType = file.getContentType();
              if (mimeType == null || mimeType.isBlank()) {
                mimeType = "image/jpeg";
              }
              String base64 = Base64.getEncoder().encodeToString(file.getBytes());
              return Map.<String, Object>of(
                  "type", "image_url",
                  "image_url", Map.of("url", "data:" + mimeType + ";base64," + base64)
              );
            } catch (IOException exception) {
              throw new IllegalStateException(exception);
            }
          })
          .toList();
    } catch (IllegalStateException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "图片读取失败");
    }
  }

  public JsonNode extractJsonObject(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start < 0 || end <= start) {
      return null;
    }
    try {
      return objectMapper.readTree(raw.substring(start, end + 1));
    } catch (IOException exception) {
      return null;
    }
  }

  public String extractAssistantText(JsonNode content) {
    if (content == null || content.isMissingNode() || content.isNull()) {
      return "";
    }
    if (content.isTextual()) {
      return content.asText("");
    }
    if (content.isArray()) {
      StringBuilder builder = new StringBuilder();
      for (JsonNode node : content) {
        if (node.isTextual()) {
          builder.append(node.asText(""));
          continue;
        }
        if ("text".equals(node.path("type").asText())) {
          builder.append(node.path("text").asText(""));
        }
      }
      return builder.toString();
    }
    return "";
  }

  private void ensureConfigured(String featureName) {
    if (apiKey.isBlank()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "服务端未配置 DASHSCOPE_API_KEY，无法调用" + featureName + "。");
    }
  }

  private String readErrorMessage(String raw, String fallbackMessage) {
    if (raw == null || raw.isBlank()) {
      return fallbackMessage;
    }
    try {
      JsonNode parsed = objectMapper.readTree(raw);
      if (parsed.path("error").path("message").isTextual()) {
        return parsed.path("error").path("message").asText();
      }
      if (parsed.path("message").isTextual()) {
        return parsed.path("message").asText();
      }
      if (parsed.path("error").path("code").isTextual()) {
        return parsed.path("error").path("code").asText();
      }
    } catch (IOException exception) {
      return raw;
    }
    return fallbackMessage;
  }
}
