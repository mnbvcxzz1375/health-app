package com.ahealth.backend.boneage;

import com.ahealth.backend.ai.AiDtos;
import com.ahealth.backend.ai.DashScopeService;
import com.ahealth.backend.ai.PromptTemplateService;
import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import com.ahealth.backend.common.JsonSupport;
import com.ahealth.backend.common.TimeFormats;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 骨龄评估服务：
 * 1. 优先调用本地 Python 推理服务（http://127.0.0.1:8013/bone-age/estimate）
 * 2. 本地服务不可用时，回退到 DashScope Vision LLM（用 prompt 估算骨龄）
 * 3. 评估结果持久化到 bone_age_tasks 表
 */
@Service
public class BoneAgeService {
  private static final Logger log = LoggerFactory.getLogger(BoneAgeService.class);

  private final JdbcTemplate jdbcTemplate;
  private final JsonSupport jsonSupport;
  private final ObjectMapper objectMapper;
  private final DashScopeService dashScopeService;
  private final PromptTemplateService promptTemplateService;
  private final HttpClient httpClient;
  private final String inferenceServiceUrl;

  public BoneAgeService(
      JdbcTemplate jdbcTemplate,
      JsonSupport jsonSupport,
      ObjectMapper objectMapper,
      DashScopeService dashScopeService,
      PromptTemplateService promptTemplateService,
      @Value("${BONE_AGE_SERVICE_URL:http://127.0.0.1:8013}") String inferenceServiceUrl
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonSupport = jsonSupport;
    this.objectMapper = objectMapper;
    this.dashScopeService = dashScopeService;
    this.promptTemplateService = promptTemplateService;
    this.inferenceServiceUrl = inferenceServiceUrl.endsWith("/")
        ? inferenceServiceUrl.substring(0, inferenceServiceUrl.length() - 1)
        : inferenceServiceUrl;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }

  /**
   * 评估骨龄（持久化结果并返回）。
   * @param file X 光图片（PNG/JPG/DICOM）
   * @return 评估结果 + 任务 ID
   */
  @Transactional
  public BoneAgeEstimateResponse estimate(MultipartFile file) {
    long userId = CurrentUser.requireUserId();
    if (file == null || file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "请上传左手腕 X 光图片。");
    }

    AiDtos.BoneAgeResult result;
    String source;
    try {
      // 先尝试本地推理服务
      result = callLocalInference(file);
      source = "local_model";
    } catch (LocalInferenceUnavailableException e) {
      log.warn("[BoneAge] 本地推理服务不可用，回退 DashScope Vision: {}", e.getMessage());
      // 回退到 LLM 视觉理解
      result = callLlmFallback(file);
      source = "llm_fallback";
    }

    // 持久化
    String taskId = "bone_" + UUID.randomUUID().toString().replace("-", "");
    String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
    jdbcTemplate.update(
        """
        INSERT INTO bone_age_tasks (id, user_id, image_name, estimated_age, confidence,
                                    growth_plate_stage, indicators_json, source, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
        """,
        taskId,
        userId,
        fileName,
        result.estimatedAgeYears(),
        result.confidence(),
        result.growthPlateStage(),
        jsonSupport.write(result.malformedIndicators() == null ? List.of() : result.malformedIndicators()),
        source
    );

    return new BoneAgeEstimateResponse(taskId, result, source, LocalDateTime.now());
  }

  /**
   * 调用本地 Python 推理服务（bone_age/inference_service.py:8013）。
   * 失败抛 LocalInferenceUnavailableException，由上层处理回退。
   */
  private AiDtos.BoneAgeResult callLocalInference(MultipartFile file) {
    String boundary = "----BoneAgeBoundary" + UUID.randomUUID().toString().replace("-", "");
    String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
    String fileName = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();

    try {
      byte[] fileBytes = file.getBytes();
      // 构造 multipart/form-data body
      String CRLF = "\r\n";
      StringBuilder pre = new StringBuilder();
      pre.append("--").append(boundary).append(CRLF);
      pre.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
          .append(fileName).append("\"").append(CRLF);
      pre.append("Content-Type: ").append(contentType).append(CRLF).append(CRLF);
      String post = CRLF + "--" + boundary + "--" + CRLF;

      byte[] body = new byte[pre.toString().getBytes(StandardCharsets.UTF_8).length
          + fileBytes.length
          + post.getBytes(StandardCharsets.UTF_8).length];
      int offset = 0;
      byte[] preBytes = pre.toString().getBytes(StandardCharsets.UTF_8);
      System.arraycopy(preBytes, 0, body, offset, preBytes.length);
      offset += preBytes.length;
      System.arraycopy(fileBytes, 0, body, offset, fileBytes.length);
      offset += fileBytes.length;
      byte[] postBytes = post.getBytes(StandardCharsets.UTF_8);
      System.arraycopy(postBytes, 0, body, offset, postBytes.length);

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(inferenceServiceUrl + "/bone-age/estimate"))
          .timeout(Duration.ofSeconds(60))
          .header("Content-Type", "multipart/form-data; boundary=" + boundary)
          .POST(HttpRequest.BodyPublishers.ofByteArray(body))
          .build();

      HttpResponse<String> response = httpClient.send(
          request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

      if (response.statusCode() != 200) {
        throw new LocalInferenceUnavailableException(
            "本地推理服务返回 " + response.statusCode() + ": " + truncate(response.body(), 200));
      }

      JsonNode json = objectMapper.readTree(response.body());
      return parseBoneAgeResult(json);
    } catch (ApiException | LocalInferenceUnavailableException e) {
      throw e;
    } catch (Exception e) {
      throw new LocalInferenceUnavailableException("本地推理调用失败: " + e.getMessage());
    }
  }

  /**
   * DashScope Vision LLM 回退：用 prompt 让 LLM 估算骨龄。
   * 这是粗估方案，置信度较低，仅用于本地模型不可用时的兜底。
   */
  private AiDtos.BoneAgeResult callLlmFallback(MultipartFile file) {
    try {
      List<Map<String, Object>> content = new ArrayList<>();
      content.add(Map.of(
          "type", "text",
          "text", "这是一张左手腕 X 光片。请评估骨龄（岁），返回 JSON 格式：\n"
              + "{\"estimatedAgeYears\": 数字, \"growthPlateStage\": \"分期描述\", "
              + "\"malformedIndicators\": [\"异常指标1\", ...]}\n"
              + "分期参考：婴幼儿期(<2)、儿童早期(2-6)、儿童晚期(6-10)、青春期前期(10-13)、"
              + "青春期(13-16)、青春期后期(16-18)、骨骺闭合期(>=18)。"
      ));
      content.addAll(dashScopeService.toImageBlocks(new MultipartFile[]{file}));

      JsonNode payload = dashScopeService.requestJson(
          promptTemplateService.render("upload.analysis_system", Map.of()),
          content,
          dashScopeService.visionModel(),
          0.2,
          "骨龄评估-LLM回退"
      );

      // 尝试从 LLM 响应中提取 JSON
      AiDtos.BoneAgeResult parsed = parseBoneAgeFromLlm(payload);
      if (parsed == null) {
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
            "LLM 回退也无法解析骨龄评估结果。");
      }
      // LLM 回退置信度上限 0.6
      float confidence = parsed.confidence() == null ? 0.5f : Math.min(parsed.confidence(), 0.6f);
      return new AiDtos.BoneAgeResult(
          parsed.estimatedAgeYears(),
          confidence,
          parsed.growthPlateStage(),
          parsed.malformedIndicators(),
          parsed.disclaimer() != null ? parsed.disclaimer() : defaultDisclaimer()
      );
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
          "骨龄评估失败（本地推理和 LLM 回退均不可用）: " + e.getMessage());
    }
  }

  /** 解析本地推理服务的 JSON 响应 */
  private AiDtos.BoneAgeResult parseBoneAgeResult(JsonNode json) {
    Float age = json.has("estimatedAgeYears") ? (float) json.get("estimatedAgeYears").asDouble() : null;
    Float confidence = json.has("confidence") ? (float) json.get("confidence").asDouble() : null;
    String stage = json.path("growthPlateStage").asText("");
    List<String> indicators = new ArrayList<>();
    JsonNode indicatorsNode = json.path("malformedIndicators");
    if (indicatorsNode.isArray()) {
      for (JsonNode item : indicatorsNode) {
        indicators.add(item.asText());
      }
    }
    String disclaimer = json.path("disclaimer").asText(defaultDisclaimer());

    if (age == null) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
          "本地推理服务返回缺少 estimatedAgeYears 字段");
    }
    return new AiDtos.BoneAgeResult(age, confidence, stage, indicators, disclaimer);
  }

  /** 从 LLM 响应中解析骨龄结果（容忍嵌套在 summary/points 等字段） */
  private AiDtos.BoneAgeResult parseBoneAgeFromLlm(JsonNode payload) {
    try {
      // LLM 可能返回 {summary: "...JSON...", ...} 或直接 JSON
      JsonNode target = payload;
      // 尝试从 summary 字段提取（与现有 UploadService.normalizeReport 风格一致）
      String summary = payload.path("summary").asText("");
      if (!summary.isBlank()) {
        String jsonStr = extractJsonFromString(summary);
        if (jsonStr != null) {
          target = objectMapper.readTree(jsonStr);
        }
      }
      Float age = target.has("estimatedAgeYears") ? (float) target.get("estimatedAgeYears").asDouble() : null;
      if (age == null) return null;
      Float confidence = target.has("confidence") ? (float) target.get("confidence").asDouble() : 0.5f;
      String stage = target.path("growthPlateStage").asText("");
      List<String> indicators = new ArrayList<>();
      JsonNode indicatorsNode = target.path("malformedIndicators");
      if (indicatorsNode.isArray()) {
        for (JsonNode item : indicatorsNode) {
          indicators.add(item.asText());
        }
      }
      return new AiDtos.BoneAgeResult(age, confidence, stage, indicators, defaultDisclaimer());
    } catch (Exception e) {
      log.warn("[BoneAge] LLM 响应解析失败: {}", e.getMessage());
      return null;
    }
  }

  /** 从字符串中提取第一个 JSON 对象（用于解析 LLM 输出中的 JSON 片段） */
  private static String extractJsonFromString(String text) {
    if (text == null) return null;
    int start = text.indexOf('{');
    if (start < 0) return null;
    int depth = 0;
    for (int i = start; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '{') depth++;
      else if (c == '}') {
        depth--;
        if (depth == 0) return text.substring(start, i + 1);
      }
    }
    return null;
  }

  private static String defaultDisclaimer() {
    return "本结果由 AI 模型自动评估，仅供参考，不能替代专业医师的临床判断。"
        + "骨龄评估受拍摄角度、光质、个体差异等因素影响，请以执业医师出具的报告为准。";
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() > max ? s.substring(0, max) + "..." : s;
  }

  /** 查询用户最近骨龄评估记录 */
  public List<BoneAgeTaskRecord> listRecent(int limit) {
    long userId = CurrentUser.requireUserId();
    return jdbcTemplate.query(
        """
        SELECT id, image_name, estimated_age, confidence, growth_plate_stage,
               indicators_json, source, created_at
        FROM bone_age_tasks
        WHERE user_id = ?
        ORDER BY created_at DESC
        LIMIT ?
        """,
        (rs, rowNum) -> new BoneAgeTaskRecord(
            rs.getString("id"),
            rs.getString("image_name"),
            rs.getFloat("estimated_age"),
            rs.getFloat("confidence"),
            rs.getString("growth_plate_stage"),
            jsonSupport.readStringList(rs.getString("indicators_json")),
            rs.getString("source"),
            TimeFormats.toIso(rs.getObject("created_at", LocalDateTime.class))
        ),
        userId,
        limit
    );
  }

  /** 本地推理不可用信号异常（用于触发回退） */
  private static class LocalInferenceUnavailableException extends RuntimeException {
    LocalInferenceUnavailableException(String msg) { super(msg); }
  }

  /** 完整评估响应（含任务 ID 用于追溯） */
  public record BoneAgeEstimateResponse(
      String taskId,
      AiDtos.BoneAgeResult result,
      String source,             // "local_model" | "llm_fallback"
      LocalDateTime estimatedAt
  ) {}

  /** 历史骨龄任务记录 */
  public record BoneAgeTaskRecord(
      String taskId,
      String imageName,
      Float estimatedAge,
      Float confidence,
      String growthPlateStage,
      List<String> indicators,
      String source,
      String createdAtIso
  ) {}
}
