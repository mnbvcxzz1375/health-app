package com.ahealth.backend.consult;

import com.ahealth.backend.ai.DashScopeService;
import com.ahealth.backend.ai.ModelRouterService;
import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import com.ahealth.backend.context.ContextDtos;
import com.ahealth.backend.context.ContextService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConsultService {
  private static final String ASSISTANT_SYSTEM_PROMPT = """
      你是中文健康管理助手。
      你只能提供健康管理、监测解读、康复训练和就医建议的辅助说明，不能替代医生诊断与治疗。
      请只返回 JSON，不要输出 Markdown，不要输出代码块。
      固定结构为：
      {"answer":"","suggestions":["","",""],"disclaimer":""}
      约束：
      1. answer 使用中文，直接回答用户问题，尽量结合场景给出 3 到 5 句清晰建议。
      2. suggestions 返回 3 条后续可追问的中文短句。
      3. disclaimer 用一句中文说明"仅用于健康管理辅助，不替代医生诊疗"。
      """;

  private final DashScopeService dashScopeService;
  private final ModelRouterService modelRouterService;
  private final ContextService contextService;
  private final HealthKnowledgeService knowledgeService;
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public ConsultService(DashScopeService dashScopeService, ModelRouterService modelRouterService,
      ContextService contextService,
      HealthKnowledgeService knowledgeService, JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.dashScopeService = dashScopeService;
    this.modelRouterService = modelRouterService;
    this.contextService = contextService;
    this.knowledgeService = knowledgeService;
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public ConsultDtos.ConsultResponse ask(ConsultDtos.ConsultQuestionRequest request) {
    String question = request.question() == null ? "" : request.question().trim();
    if (question.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "请输入问题内容。");
    }

    // Domain restriction: reject non-health questions
    if (!knowledgeService.isHealthRelated(question)) {
      return new ConsultDtos.ConsultResponse(
          "consult_guarded_" + UUID.randomUUID().toString().replace("-", ""),
          "我是健康管理助手，只能回答健康、用药、康复、饮食等相关问题。您可以问我关于血压管理、睡眠改善、运动康复、药物使用等方面的问题。",
          List.of("帮我看看今天的健康数据", "最近睡眠不好怎么办", "我的用药有什么注意事项"),
          "仅用于健康管理辅助，不替代医生诊疗。"
      );
    }

    long uid = CurrentUser.requireUserId();

    // Retrieve knowledge from RAG
    List<String> knowledge = knowledgeService.retrieveKnowledge(uid, question);
    String knowledgeBlock = "";
    if (!knowledge.isEmpty()) {
      knowledgeBlock = "\n相关知识：\n" + String.join("\n", knowledge.stream().map(k -> "- " + k).toList());
    }

    // Build context-aware message with knowledge injection
    String userMessage = buildContextAwareMessage(request.scene(), question) + knowledgeBlock;

    JsonNode payload = dashScopeService.requestJson(
        ASSISTANT_SYSTEM_PROMPT,
        userMessage,
        dashScopeService.chatModel(),
        0.35,
        "智能助手"
    );

    String answer = normalizeText(payload.path("answer").asText(""), "建议先结合近期监测趋势、症状变化和康复安排综合判断。");
    List<String> suggestions = normalizeSuggestions(payload.path("suggestions"));
    String disclaimer = normalizeText(
        payload.path("disclaimer").asText(""),
        "以上内容仅用于健康管理辅助，不替代医生诊疗。"
    );

    String requestId = "consult_" + UUID.randomUUID().toString().replace("-", "");
    asyncSaveMemory(requestId, question, answer);

    // Persist to consult_history
    saveHistory(uid, requestId, normalizeScene(request.scene()), question, answer,
        suggestions, disclaimer, knowledge, dashScopeService.chatModel());

    return new ConsultDtos.ConsultResponse(requestId, answer, suggestions, disclaimer);
  }

  private String buildContextAwareMessage(String scene, String question) {
    StringBuilder sb = new StringBuilder();
    sb.append("场景：").append(normalizeScene(scene)).append("\n");

    try {
      ContextDtos.ContextSnapshot ctx = contextService.getSnapshot();

      if (ctx.systemSummary() != null && !ctx.systemSummary().isBlank()) {
        sb.append("用户画像：").append(ctx.systemSummary()).append("\n");
      }

      if (ctx.dailySummary() != null && !ctx.dailySummary().isBlank()) {
        sb.append("今日监测：").append(ctx.dailySummary()).append("\n");
      }

      if (ctx.activeConcerns() != null && !ctx.activeConcerns().isEmpty()) {
        sb.append("当前关注：").append(String.join("；", ctx.activeConcerns())).append("\n");
      }

      if (ctx.currentMedications() != null && !ctx.currentMedications().isEmpty()) {
        sb.append("当前用药：").append(String.join("、", ctx.currentMedications())).append("\n");
      }

      if (ctx.memories() != null && !ctx.memories().isEmpty()) {
        List<String> recentMemories = ctx.memories().stream()
            .limit(3)
            .map(ContextDtos.MemoryEntry::content)
            .toList();
        sb.append("历史记忆：").append(String.join("；", recentMemories)).append("\n");
      }
    } catch (Exception ignored) {
      // Context unavailable — proceed without context
    }

    sb.append("问题：").append(question);
    return sb.toString();
  }

  private void asyncSaveMemory(String requestId, String question, String answer) {
    try {
      String memoryContent = "用户问：" + truncate(question, 80) + " → 回答要点：" + truncate(answer, 120);
      contextService.saveMemory(new ContextDtos.SaveMemoryRequest("consult", memoryContent));
    } catch (Exception ignored) {
      // Memory save is best-effort
    }
  }

  private String truncate(String text, int maxLen) {
    if (text == null) return "";
    return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
  }

  private String normalizeScene(String scene) {
    String text = scene == null ? "" : scene.trim();
    return text.isBlank() ? "assistant" : text;
  }

  private List<String> normalizeSuggestions(JsonNode node) {
    if (!node.isArray()) {
      return List.of("我今天适合做什么强度的训练？", "最近睡眠一般，今晚怎么调整？", "上传报告后应该先看哪些风险提示？");
    }
    List<String> values = new ArrayList<>();
    for (JsonNode item : node) {
      String text = normalizeText(item.asText(""), "");
      if (!text.isBlank() && !looksLikeDisclaimer(text)) {
        values.add(text);
      }
    }
    if (values.size() < 3) {
      return List.of("我今天适合做什么强度的训练？", "最近睡眠一般，今晚怎么调整？", "上传报告后应该先看哪些风险提示？");
    }
    return values.subList(0, Math.min(3, values.size()));
  }

  private boolean looksLikeDisclaimer(String text) {
    return text.contains("本建议") || text.contains("非医疗") || text.contains("遵医嘱")
        || text.contains("不替代") || text.contains("仅供参考") || text.contains("请咨询医生")
        || text.contains("专业医疗") || text.contains("医疗诊断") || text.contains("咨询医生");
  }

  private String normalizeText(String value, String fallback) {
    String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
    return text.isBlank() ? fallback : text;
  }

  // === History CRUD ===

  public List<Map<String, Object>> getHistory(int limit, int offset) {
    long uid = CurrentUser.requireUserId();
    return jdbc.queryForList(
        "SELECT id, request_id, scene, question, answer, suggestions_json, disclaimer, "
        + "knowledge_sources_json, model_used, created_at "
        + "FROM consult_history WHERE user_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?",
        uid, limit, offset);
  }

  public Map<String, Object> deleteHistoryItem(int id) {
    long uid = CurrentUser.requireUserId();
    jdbc.update("DELETE FROM consult_history WHERE id=? AND user_id=?", id, uid);
    return Map.of("success", true);
  }

  public Map<String, Object> clearHistory() {
    long uid = CurrentUser.requireUserId();
    int deleted = jdbc.update("DELETE FROM consult_history WHERE user_id=?", uid);
    return Map.of("success", true, "deleted", deleted);
  }

  private void saveHistory(long uid, String requestId, String scene, String question,
      String answer, List<String> suggestions, String disclaimer,
      List<String> knowledgeSources, String modelUsed) {
    try {
      jdbc.update(
          "INSERT INTO consult_history(user_id,request_id,scene,question,answer,suggestions_json,"
          + "disclaimer,knowledge_sources_json,model_used,created_at) VALUES(?,?,?,?,?,?,?,?,?,NOW())",
          uid, requestId, scene, question, answer,
          toJson(suggestions), disclaimer, toJson(knowledgeSources), modelUsed);
    } catch (Exception ignored) {
      // History save is best-effort
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }
}
