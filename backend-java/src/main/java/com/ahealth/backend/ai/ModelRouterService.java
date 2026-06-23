package com.ahealth.backend.ai;

import com.ahealth.backend.context.ContextDtos;
import com.ahealth.backend.context.ContextService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModelRouterService {
  private final OpenMedService openMedService;
  private final PiiScrubService piiScrubService;
  private final MedicalNerService medicalNerService;
  private final DashScopeService dashScopeService;
  private final ContextService contextService;
  private final ObjectMapper objectMapper;

  public ModelRouterService(
      OpenMedService openMedService,
      PiiScrubService piiScrubService,
      MedicalNerService medicalNerService,
      DashScopeService dashScopeService,
      ContextService contextService,
      ObjectMapper objectMapper
  ) {
    this.openMedService = openMedService;
    this.piiScrubService = piiScrubService;
    this.medicalNerService = medicalNerService;
    this.dashScopeService = dashScopeService;
    this.contextService = contextService;
    this.objectMapper = objectMapper;
  }

  /**
   * Route a health question through the optimal model pipeline.
   * Returns the raw JSON string from the LLM (answer/suggestions/disclaimer).
   */
  public String routeHealthQuestion(String question, String scene) {
    // Step 1: PII scrub
    AiDtos.PiiScrubResult scrubResult = piiScrubService.scrub(question);
    String safeQuestion = scrubResult.scrubbedText();

    // Step 2: Get user context
    String contextBlock = buildContextBlock();

    // Step 3: Determine model based on question content
    String intent = classifyIntent(safeQuestion);

    // Step 4: Build prompt and call model
    String systemPrompt = buildSystemPrompt(intent, scene);
    String userMessage = contextBlock + "\n问题：" + safeQuestion;

    JsonNode payload;
    if (openMedService.isConfigured() && intent.equals("medication")) {
      payload = dashScopeService.requestJson(
          systemPrompt, userMessage, dashScopeService.chatModel(), 0.35, "药物咨询");
    } else {
      payload = dashScopeService.requestJson(
          systemPrompt, userMessage, dashScopeService.chatModel(), 0.35, "健康咨询");
    }

    // Step 5: Restore PII in response if needed
    String answer = piiScrubService.restore(
        dashScopeService.extractAssistantText(payload.path("choices").path(0).path("message").path("content")),
        scrubResult.masks());

    // Return as proper JSON string
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      return "{\"answer\":\"" + escapeJson(answer) + "\",\"suggestions\":[],\"disclaimer\":\"仅用于健康管理辅助。\"}";
    }
  }

  private String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r")
            .replace("\t", "\\t");
  }

  private String classifyIntent(String question) {
    String q = question.toLowerCase();
    if (q.contains("药") || q.contains("服") || q.contains("剂量") || q.contains("禁忌")) {
      return "medication";
    }
    if (q.contains("血压") || q.contains("高血压") || q.contains("低血压")) {
      return "blood_pressure";
    }
    if (q.contains("睡") || q.contains("失眠") || q.contains("睡眠")) {
      return "sleep";
    }
    if (q.contains("运动") || q.contains("康复") || q.contains("锻炼")) {
      return "exercise";
    }
    return "general";
  }

  private String buildSystemPrompt(String intent, String scene) {
    String base = "你是中文健康管理助手，只提供健康管理辅助说明，不能替代医生诊断。";
    return switch (intent) {
      case "medication" -> base + "你专注于用药安全和药物管理。回答时优先考虑药物相互作用、剂量安全和服药时间。";
      case "blood_pressure" -> base + "你专注于血压管理。结合用户血压数据给出个性化建议。";
      case "sleep" -> base + "你专注于睡眠健康。结合用户睡眠数据给出改善建议。";
      case "exercise" -> base + "你专注于运动康复。结合用户活动数据给出安全的运动建议。";
      default -> base + "请根据用户健康数据给出 3 到 5 句清晰建议。";
    };
  }

  private String buildContextBlock() {
    try {
      ContextDtos.ContextSnapshot ctx = contextService.getSnapshot();
      StringBuilder sb = new StringBuilder("用户健康上下文：\n");
      if (ctx.systemSummary() != null) sb.append("画像：").append(ctx.systemSummary()).append("\n");
      if (ctx.dailySummary() != null) sb.append("今日：").append(ctx.dailySummary()).append("\n");
      if (ctx.activeConcerns() != null && !ctx.activeConcerns().isEmpty()) {
        sb.append("关注：").append(String.join("；", ctx.activeConcerns())).append("\n");
      }
      if (ctx.currentMedications() != null && !ctx.currentMedications().isEmpty()) {
        sb.append("用药：").append(String.join("、", ctx.currentMedications())).append("\n");
      }
      return sb.toString();
    } catch (Exception e) {
      return "";
    }
  }
}
