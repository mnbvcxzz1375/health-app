package com.ahealth.backend.consult;

import com.ahealth.backend.ai.DashScopeService;
import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.context.ContextDtos;
import com.ahealth.backend.context.ContextService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
  private final ContextService contextService;

  public ConsultService(DashScopeService dashScopeService, ContextService contextService) {
    this.dashScopeService = dashScopeService;
    this.contextService = contextService;
  }

  public ConsultDtos.ConsultResponse ask(ConsultDtos.ConsultQuestionRequest request) {
    String question = request.question() == null ? "" : request.question().trim();
    if (question.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "请输入问题内容。");
    }

    String userMessage = buildContextAwareMessage(request.scene(), question);

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
      if (!text.isBlank()) {
        values.add(text);
      }
    }
    if (values.size() < 3) {
      return List.of("我今天适合做什么强度的训练？", "最近睡眠一般，今晚怎么调整？", "上传报告后应该先看哪些风险提示？");
    }
    return values.subList(0, Math.min(3, values.size()));
  }

  private String normalizeText(String value, String fallback) {
    String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
    return text.isBlank() ? fallback : text;
  }
}
