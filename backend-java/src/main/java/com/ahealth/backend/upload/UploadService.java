package com.ahealth.backend.upload;

import com.ahealth.backend.ai.DashScopeService;
import com.ahealth.backend.ai.LlmCacheService;
import com.ahealth.backend.ai.PromptTemplateService;
import com.ahealth.backend.boneage.BoneAgeService;
import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import com.ahealth.backend.common.JsonSupport;
import com.ahealth.backend.common.TimeFormats;
import com.ahealth.backend.rag.RagDtos;
import com.ahealth.backend.rag.RagSearchService;
import com.ahealth.backend.rehab.RehabDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {

  private static final Logger log = LoggerFactory.getLogger(UploadService.class);
  private static final Duration CACHE_TTL = Duration.ofHours(336); // 14 天（康复计划相对稳定）

  private final JdbcTemplate jdbcTemplate;
  private final JsonSupport jsonSupport;
  private final DashScopeService dashScopeService;
  private final PromptTemplateService promptTemplateService;
  private final LlmCacheService llmCacheService;
  private final RagSearchService ragSearchService;
  private final ObjectMapper objectMapper;
  private final BoneAgeService boneAgeService;

  public UploadService(JdbcTemplate jdbcTemplate, JsonSupport jsonSupport, DashScopeService dashScopeService,
      PromptTemplateService promptTemplateService,
      LlmCacheService llmCacheService,
      RagSearchService ragSearchService,
      ObjectMapper objectMapper,
      BoneAgeService boneAgeService) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonSupport = jsonSupport;
    this.dashScopeService = dashScopeService;
    this.promptTemplateService = promptTemplateService;
    this.llmCacheService = llmCacheService;
    this.ragSearchService = ragSearchService;
    this.objectMapper = objectMapper;
    this.boneAgeService = boneAgeService;
  }

  @Transactional
  public UploadDtos.AnalyzeTaskResponse createTask(String type, String text, MultipartFile[] files) {
    long userId = CurrentUser.requireUserId();
    String normalizedType = normalizeType(type);
    MultipartFile[] normalizedFiles = normalizeFiles(files);
    String normalizedText = text == null ? "" : text.trim();
    if (normalizedText.isBlank() && normalizedFiles.length == 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "请先上传文件、图片或输入分析内容。");
    }

    String taskId = "task_" + UUID.randomUUID().toString().replace("-", "");
    String fileName = normalizedFiles.length == 0
        ? ""
        : Arrays.stream(normalizedFiles)
            .map(MultipartFile::getOriginalFilename)
            .filter(Objects::nonNull)
            .reduce((a, b) -> a + " | " + b)
            .orElse("");

    UploadDtos.AnalyzeReport report = analyzeByModel(normalizedType, normalizedText, normalizedFiles);
    jdbcTemplate.update(
        """
        INSERT INTO analyze_tasks (id, user_id, type, file_name, text_content, status, points_json, advice_json, report_json, saved, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, 'DONE', ?, ?, ?, 0, NOW(), NOW())
        """,
        taskId,
        userId,
        normalizedType,
        fileName,
        normalizedText,
        jsonSupport.write(report.points()),
        jsonSupport.write(report.advice()),
        jsonSupport.write(report)
    );
    return new UploadDtos.AnalyzeTaskResponse(taskId);
  }

  /**
   * 自定义模型上传分析路由：
   * - type="bone" → 骨龄评估（BoneAgeService.estimate）
   * - 其他类型 → 复用结构化上传分析（source=llm_fallback），不再返回 501
   *
   * 注意：骨龄评估走单独的 bone_age_tasks 表，不复用 analyze_tasks。
   * 返回的 CustomModelTaskResponse 同时包含 taskId 和（bone 情况下）完整结果，
   * 前端可直接渲染，无需二次查询。
   */
  @Transactional
  public UploadDtos.CustomModelTaskResponse createTaskByCustomModel(String type, String text, MultipartFile[] files) {
    String normalizedType = normalizeType(type);
    MultipartFile[] normalizedFiles = normalizeFiles(files);

    if ("bone".equals(normalizedType)) {
      if (normalizedFiles.length == 0) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "骨龄评估需要上传 X 光图片。");
      }
      BoneAgeService.BoneAgeEstimateResponse estimate = boneAgeService.estimate(normalizedFiles[0]);
      return new UploadDtos.CustomModelTaskResponse(
          estimate.taskId(),
          "bone",
          estimate.source(),
          estimate.result(),
          null
      );
    }

    // Older clients use this endpoint for the structured health-analysis
    // types. Reuse the persisted analysis path and expose the fallback source.
    UploadDtos.AnalyzeTaskResponse task = createTask(normalizedType, text, normalizedFiles);
    UploadDtos.AnalyzeResultResponse result = getTask(task.taskId());
    return new UploadDtos.CustomModelTaskResponse(
        task.taskId(), normalizedType, "llm_fallback", null, result.report());
  }

  public UploadDtos.AnalyzeResultResponse getTask(String taskId) {
    long userId = CurrentUser.requireUserId();
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        """
        SELECT status, points_json, advice_json, report_json, saved
        FROM analyze_tasks
        WHERE id = ? AND user_id = ?
        LIMIT 1
        """,
        taskId,
        userId
    );
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "任务不存在。");
    }
    Map<String, Object> row = rows.get(0);
    return new UploadDtos.AnalyzeResultResponse(
        String.valueOf(row.get("status")),
        jsonSupport.readStringList(stringValue(row.get("points_json"))),
        jsonSupport.readStringList(stringValue(row.get("advice_json"))),
        mapReport(row.get("report_json")),
        boolValue(row.get("saved")),
        null
    );
  }

  @Transactional
  public UploadDtos.AnalyzeSaveResponse saveTask(String taskId) {
    long userId = CurrentUser.requireUserId();
    int updated = jdbcTemplate.update(
        """
        UPDATE analyze_tasks
        SET saved = 1, updated_at = NOW()
        WHERE id = ? AND user_id = ? AND status = 'DONE'
        """,
        taskId,
        userId
    );
    if (updated == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "任务不存在。");
    }

    List<Map<String, Object>> reportRows = jdbcTemplate.queryForList(
        """
        SELECT id, type, report_json, updated_at
        FROM analyze_tasks
        WHERE user_id = ? AND saved = 1 AND status = 'DONE'
        ORDER BY updated_at DESC
        LIMIT 3
        """,
        userId
    );
    return new UploadDtos.AnalyzeSaveResponse(true, true, buildRehabPlanDraftByModel(userId, reportRows));
  }

  @Transactional
  public Map<String, Boolean> deleteTask(String taskId) {
    long userId = CurrentUser.requireUserId();
    int updated = jdbcTemplate.update("DELETE FROM analyze_tasks WHERE id = ? AND user_id = ?", taskId, userId);
    if (updated == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "任务不存在。");
    }
    return Map.of("success", true);
  }

  public List<UploadDtos.SavedAnalyzeReport> listSavedReports() {
    long userId = CurrentUser.requireUserId();
    return jdbcTemplate.query(
        """
        SELECT id, type, file_name, report_json, created_at, updated_at
        FROM analyze_tasks
        WHERE user_id = ? AND saved = 1 AND status = 'DONE'
        ORDER BY updated_at DESC
        """,
        (rs, rowNum) -> new UploadDtos.SavedAnalyzeReport(
            rs.getString("id"),
            rs.getString("type"),
            rs.getString("file_name"),
            TimeFormats.toIso(rs.getObject("created_at", LocalDateTime.class)),
            TimeFormats.toIso(rs.getObject("updated_at", LocalDateTime.class)),
            mapReport(rs.getString("report_json"))
        ),
        userId
    );
  }

  private UploadDtos.AnalyzeReport analyzeByModel(String type, String text, MultipartFile[] files) {
    List<Map<String, Object>> content = new ArrayList<>();
    content.add(Map.of(
        "type", "text",
        "text", "资料类型：" + typeLabel(type) + "\n请根据本次上传内容输出结构化健康管理分析报告。"
    ));
    if (!text.isBlank()) {
      content.add(Map.of("type", "text", "text", "用户补充文字：\n" + text.substring(0, Math.min(text.length(), 6000))));
    }
    for (MultipartFile file : files) {
      if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
        content.addAll(dashScopeService.toImageBlocks(new MultipartFile[] {file}));
        continue;
      }
      content.add(Map.of("type", "text", "text", buildFileTextFallback(file)));
    }

    JsonNode payload = dashScopeService.requestJson(
        promptTemplateService.render("upload.analysis_system", Map.of()),
        content,
        dashScopeService.visionModel(),
        0.2,
        "上传分析"
    );
    return normalizeReport(payload, type);
  }

  private RehabDtos.RehabPlanDraft buildRehabPlanDraftByModel(long userId, List<Map<String, Object>> reportRows) {
    if (reportRows == null || reportRows.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "缺少可用于生成康复计划的报告。");
    }

    List<Map<String, Object>> exerciseRows = jdbcTemplate.queryForList(
        """
        SELECT name, category, duration, level, minutes, steps_json, caution, focus, benefits_json, video_minutes
        FROM rehab_exercises
        WHERE user_id IS NULL OR user_id = ?
        ORDER BY CASE WHEN user_id = ? THEN 0 ELSE 1 END, id ASC
        """,
        userId,
        userId
    );

    List<Map<String, Object>> reportPayload = reportRows.stream()
        .map(row -> Map.of(
            "taskId", stringValue(row.get("id")),
            "type", stringValue(row.get("type")),
            "updatedAt", stringValue(row.get("updated_at")),
            "report", mapReport(row.get("report_json"))
        ))
        .toList();

    String content = """
        当前用户最近纳入计划生成的报告如下：
        %s

        当前可复用的动作库如下：
        %s
        """.formatted(jsonSupport.write(reportPayload), jsonSupport.write(exerciseRows));

    // === Layer 2: LLM 缓存命中检查（0 LLM 调用） ===
    String promptKey = buildRehabPromptKey(reportPayload, exerciseRows);
    String contextHash = buildRehabContextHash(userId);
    try {
      var cached = llmCacheService.getExact("rehab_plan_draft", promptKey, contextHash);
      if (cached.isPresent()) {
        try {
          JsonNode payload = objectMapper.readTree(cached.get());
          RehabDtos.RehabPlanDraft draft = normalizeRehabPlanDraft(payload,
              reportRows.stream().map(row -> stringValue(row.get("id"))).toList());
          log.info("[RehabPlan] 缓存命中: userId={} reports={}", userId, reportRows.size());
          return draft;
        } catch (Exception parseEx) {
          log.debug("[RehabPlan] 缓存 JSON 解析失败，继续走正常流程: {}", parseEx.getMessage());
        }
      }
    } catch (Exception e) {
      log.warn("[RehabPlan] 缓存查询失败，跳过: {}", e.getMessage());
    }

    // === Layer 3: RAG 检索康复指导知识（docType="rehab_guide"） ===
    String rehabKnowledge = retrieveRehabKnowledge(reportPayload);

    // === Layer 4: LLM 调用（注入知识块到 ddi_rules / 上下文） ===
    String systemPrompt = promptTemplateService.render("upload.rehab_plan_draft_system", Map.of(
        "recent_reports", jsonSupport.write(reportPayload),
        "exercise_library", jsonSupport.write(exerciseRows),
        "bmi", "",
        "target_calories", rehabKnowledge
    ));

    JsonNode payload = dashScopeService.requestJson(
        systemPrompt,
        content,
        dashScopeService.chatModel(),
        0.2,
        "康复计划生成"
    );
    RehabDtos.RehabPlanDraft draft = normalizeRehabPlanDraft(payload,
        reportRows.stream().map(row -> stringValue(row.get("id"))).toList());

    // === Layer 5: 写缓存（best-effort） ===
    try {
      llmCacheService.put("rehab_plan_draft", promptKey, contextHash,
          objectMapper.writeValueAsString(payload), CACHE_TTL, true);
    } catch (Exception e) {
      log.debug("[RehabPlan] 写缓存失败: {}", e.getMessage());
    }

    return draft;
  }

  /**
   * 基于报告内容 + 动作库摘要构造缓存 promptKey。
   */
  private String buildRehabPromptKey(List<Map<String, Object>> reportPayload, List<Map<String, Object>> exerciseRows) {
    StringBuilder sb = new StringBuilder("rehab:");
    for (Map<String, Object> r : reportPayload) {
      sb.append(r.getOrDefault("taskId", "")).append("|")
          .append(r.getOrDefault("type", "")).append("|");
    }
    sb.append("|exercises:").append(exerciseRows.size());
    return sb.toString();
  }

  /**
   * 构造 contextHash：基于 userId（康复计划与用户强相关）。
   */
  private String buildRehabContextHash(long userId) {
    try {
      String raw = "rehab|user|" + userId;
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      return "";
    }
  }

  /**
   * RAG 检索康复指导知识（docType="rehab_guide"）；失败返回空字符串。
   * 查询关键词基于最近报告的 rehabFocus + summary。
   */
  private String retrieveRehabKnowledge(List<Map<String, Object>> reportPayload) {
    try {
      String query = extractRehabQuery(reportPayload);
      if (query.isBlank()) return "";
      List<RagDtos.RagSearchHit> hits = ragSearchService.search(query, "rehab_guide", 5);
      if (hits == null || hits.isEmpty()) return "";
      StringBuilder sb = new StringBuilder();
      for (RagDtos.RagSearchHit hit : hits) {
        if (hit.chunkText() != null && !hit.chunkText().isBlank()) {
          sb.append("- ").append(hit.chunkText().trim()).append("\n");
        }
      }
      return sb.toString().trim();
    } catch (Exception e) {
      log.debug("[RehabPlan] RAG 检索失败: {}", e.getMessage());
      return "";
    }
  }

  /** 从报告 payload 中提取康复相关查询关键词。 */
  private String extractRehabQuery(List<Map<String, Object>> reportPayload) {
    StringBuilder sb = new StringBuilder();
    for (Map<String, Object> r : reportPayload) {
      Object reportObj = r.get("report");
      if (reportObj instanceof UploadDtos.AnalyzeReport report) {
        String focus = report.rehabFocus();
        if (focus != null && !focus.isBlank()) {
          sb.append(focus).append(" ");
        }
        String summary = report.summary();
        if (summary != null && !summary.isBlank()) {
          sb.append(summary).append(" ");
        }
      }
    }
    return sb.toString().trim();
  }

  private UploadDtos.AnalyzeReport normalizeReport(JsonNode payload, String type) {
    String title = normalizeText(payload.path("title").asText(""), typeLabel(type) + "分析报告");
    String summary = normalizeText(payload.path("summary").asText(""), "已完成本次资料的结构化分析，请结合医生意见综合判断。");
    String riskLevel = normalizeRiskLevel(payload.path("riskLevel").asText(""));
    String rehabFocus = normalizeText(payload.path("rehabFocus").asText(""), "优先进行低到中强度恢复训练，减少额外负荷。");
    String caution = normalizeText(
        payload.path("caution").asText(""),
        "以上结果仅用于健康管理辅助，不替代医生诊疗与正式报告结论。"
    );
    return new UploadDtos.AnalyzeReport(
        title,
        summary,
        riskLevel,
        normalizeList(payload.path("points"), List.of("当前资料已完成结构化分析。", "建议结合近期症状和监测数据综合判断。"), 2, 4),
        normalizeList(payload.path("advice"), List.of("优先保持低到中强度活动。", "如症状持续或加重，请尽快线下复诊。"), 2, 4),
        rehabFocus,
        normalizeList(payload.path("followUp"), List.of("建议保留本次报告用于后续对比。", "后续如新增症状，可再次上传资料。"), 2, 4),
        caution
    );
  }

  private RehabDtos.RehabPlanDraft normalizeRehabPlanDraft(JsonNode payload, List<String> sourceTaskIds) {
    JsonNode summaryNode = payload.path("summary");
    JsonNode reminderNode = payload.path("reminder");
    JsonNode exercisesNode = payload.path("exercises");

    if (!exercisesNode.isArray() || exercisesNode.size() != 4) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "康复计划草案动作数量无效。");
    }

    List<RehabDtos.DraftExerciseCandidate> exercises = new ArrayList<>();
    for (JsonNode exerciseNode : exercisesNode) {
      exercises.add(normalizeDraftExercise(exerciseNode));
    }

    String time = normalizeTime(reminderNode.path("time").asText(""));
    List<String> days = normalizeReminderDays(reminderNode.path("days"));
    boolean pushEnabled = !reminderNode.has("pushEnabled") || reminderNode.path("pushEnabled").asBoolean(true);

    return new RehabDtos.RehabPlanDraft(
        sourceTaskIds,
        new RehabDtos.RehabPlanSummary(
            normalizeText(summaryNode.path("focus").asText(""), "核心稳定"),
            normalizeText(summaryNode.path("frequency").asText(""), "每周 3 次"),
            normalizeText(summaryNode.path("duration").asText(""), "单次 20 分钟"),
            normalizeText(summaryNode.path("intensity").asText(""), "低到中等强度")
        ),
        exercises,
        new RehabDtos.PlanReminderDraft(time, days, pushEnabled)
    );
  }

  private RehabDtos.DraftExerciseCandidate normalizeDraftExercise(JsonNode node) {
    String mode = normalizeMode(node.path("mode").asText(""));
    String name = requireText(node.path("name").asText(""), "康复计划草案缺少动作名称。");
    String category = requireText(node.path("category").asText(""), "康复计划草案缺少动作分类。");
    String duration = requireText(node.path("duration").asText(""), "康复计划草案缺少动作时长。");
    String level = normalizeLevel(node.path("level").asText(""));
    int minutes = Math.max(1, node.path("minutes").asInt(8));
    List<String> steps = normalizeList(node.path("steps"), List.of("保持动作稳定", "在无痛范围内完成"), 2, 5);
    String caution = requireText(node.path("caution").asText(""), "康复计划草案缺少注意事项。");
    String focus = requireText(node.path("focus").asText(""), "康复计划草案缺少训练重点。");
    List<String> benefits = normalizeList(node.path("benefits"), List.of("改善动作控制"), 1, 4);
    int videoMinutes = Math.max(1, node.path("videoMinutes").asInt(5));
    return new RehabDtos.DraftExerciseCandidate(mode, name, category, duration, level, minutes, steps, caution, focus, benefits, videoMinutes);
  }

  private UploadDtos.AnalyzeReport mapReport(Object raw) {
    Map<String, Object> value = raw instanceof String string ? jsonSupport.readObject(string) : Map.of();
    return new UploadDtos.AnalyzeReport(
        stringValue(value.get("title")),
        stringValue(value.get("summary")),
        stringValue(value.get("riskLevel")),
        stringList(value.get("points")),
        stringList(value.get("advice")),
        stringValue(value.get("rehabFocus")),
        stringList(value.get("followUp")),
        stringValue(value.get("caution"))
    );
  }

  private List<String> stringList(Object value) {
    if (value instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return List.of();
  }

  private MultipartFile[] normalizeFiles(MultipartFile[] files) {
    return Arrays.stream(files == null ? new MultipartFile[0] : files)
        .filter(file -> file != null && !file.isEmpty())
        .toArray(MultipartFile[]::new);
  }

  private List<String> normalizeList(JsonNode value, List<String> fallback, int min, int max) {
    List<String> items = new ArrayList<>();
    if (value.isArray()) {
      for (JsonNode node : value) {
        String text = normalizeText(node.asText(""), "");
        if (!text.isBlank()) {
          items.add(text);
        }
      }
    }
    if (items.size() < min) {
      return fallback;
    }
    return items.subList(0, Math.min(max, items.size()));
  }

  private List<String> normalizeReminderDays(JsonNode value) {
    List<String> days = new ArrayList<>();
    if (value.isArray()) {
      for (JsonNode node : value) {
        String day = node.asText("").trim().toLowerCase();
        if (List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun").contains(day)) {
          days.add(day);
        }
      }
    }
    return days.isEmpty() ? List.of("mon", "wed", "fri") : days;
  }

  private String normalizeType(String type) {
    return switch (type == null ? "" : type.trim()) {
      case "image", "lab", "text", "symptom", "bone" -> type.trim();
      default -> "text";
    };
  }

  private String typeLabel(String type) {
    return switch (normalizeType(type)) {
      case "image" -> "影像资料";
      case "lab" -> "化验报告";
      case "symptom" -> "症状描述";
      case "bone" -> "骨龄评估";
      default -> "文字报告";
    };
  }

  private String normalizeText(String value, String fallback) {
    String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
    return text.isBlank() ? fallback : text;
  }

  private String requireText(String value, String message) {
    String text = normalizeText(value, "");
    if (text.isBlank()) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, message);
    }
    return text;
  }

  private String normalizeRiskLevel(String riskLevel) {
    String text = normalizeText(riskLevel, "");
    return switch (text) {
      case "低风险", "中等风险", "高风险" -> text;
      default -> "中等风险";
    };
  }

  private String normalizeMode(String mode) {
    String text = normalizeText(mode, "").toLowerCase();
    return "existing".equals(text) ? "existing" : "generated";
  }

  private String normalizeLevel(String level) {
    String text = normalizeText(level, "");
    if (List.of("进阶", "advanced", "进阶级").contains(text)) {
      return "进阶";
    }
    return "基础";
  }

  private String normalizeTime(String time) {
    String text = normalizeText(time, "");
    return text.matches("^\\d{2}:\\d{2}$") ? text : "08:00";
  }

  private String buildFileTextFallback(MultipartFile file) {
    String name = file.getOriginalFilename() == null ? "未命名文件" : file.getOriginalFilename();
    String contentType = file.getContentType() == null ? "" : file.getContentType();
    if (contentType.startsWith("text/")) {
      try {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        if (!text.isBlank()) {
          return "文本文件《" + name + "》内容：\n" + text.substring(0, Math.min(text.length(), 8000));
        }
      } catch (IOException ignored) {
      }
    }
    return "文件《" + name + "》当前未提取到正文，请至少参考文件名：" + name;
  }

  private Boolean boolValue(Object value) {
    return value instanceof Number number && number.intValue() == 1;
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
