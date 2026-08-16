package com.ahealth.backend.ai;

import com.ahealth.backend.common.CurrentUser;
import com.ahealth.backend.consult.ConsultAgent;
import com.ahealth.backend.context.ContextDtos;
import com.ahealth.backend.context.ContextService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class ModelRouterService {
  private static final Logger log = LoggerFactory.getLogger(ModelRouterService.class);

  private final OpenMedService openMedService;
  private final PiiScrubService piiScrubService;
  private final MedicalNerService medicalNerService;
  private final DashScopeService dashScopeService;
  private final ContextService contextService;
  private final ObjectMapper objectMapper;
  private final PromptTemplateService promptTemplateService;
  private final ConsultAgent consultAgent;

  public ModelRouterService(
      OpenMedService openMedService,
      PiiScrubService piiScrubService,
      MedicalNerService medicalNerService,
      DashScopeService dashScopeService,
      ContextService contextService,
      ObjectMapper objectMapper,
      PromptTemplateService promptTemplateService,
      @Lazy ConsultAgent consultAgent
  ) {
    this.openMedService = openMedService;
    this.piiScrubService = piiScrubService;
    this.medicalNerService = medicalNerService;
    this.dashScopeService = dashScopeService;
    this.contextService = contextService;
    this.objectMapper = objectMapper;
    this.promptTemplateService = promptTemplateService;
    this.consultAgent = consultAgent;
  }

  /**
   * Route a health question through the optimal model pipeline.
   * Returns JSON string in {answer, suggestions, disclaimer} format.
   *
   * <p>委托 {@link #routeHealthQuestion(String, String, long)}，userId 从 {@link CurrentUser} 取。
   */
  public String routeHealthQuestion(String question, String scene) {
    return routeHealthQuestion(question, scene, CurrentUser.requireUserId());
  }

  /**
   * Route a health question through the optimal model pipeline.
   * Returns JSON string in {answer, suggestions, disclaimer} format.
   *
   * <p>路由策略：
   * <ul>
   *   <li>intent=general → 优先走 {@link ConsultAgent}（LangChain4j ReAct + 4 Tools），
   *       Agent 返回 null 或格式无效时 fallback 到 DashScope 单轮</li>
   *   <li>其他 intent → 直接走 DashScope 单轮（含 OpenMed 药物咨询分支）</li>
   * </ul>
   *
   * @param userId 当前用户 ID（Agent 路径需要用于 SecurityContext 切换 + Tool 内部 CurrentUser 调用）
   */
  public String routeHealthQuestion(String question, String scene, long userId) {
    // Step 1: PII scrub（仅用于 DashScope fallback 路径，Agent 路径不做 scrub）
    AiDtos.PiiScrubResult scrubResult = piiScrubService.scrub(question);
    String safeQuestion = scrubResult.scrubbedText();

    // Step 2: Classify intent
    String intent = classifyIntent(safeQuestion);

    // Step 3: intent=general 时优先走 ConsultAgent（LangChain4j ReAct + 4 Tools）
    if (intent.equals("general")) {
      try {
        String agentResult = consultAgent.ask(question, userId);
        if (agentResult != null && !agentResult.isBlank()) {
          String normalized = normalizeAgentJson(agentResult);
          if (normalized != null) {
            return normalized;
          }
        }
        log.info("[ModelRouter] Agent 返回 null 或格式无效，fallback 到 DashScope 单轮");
      } catch (Exception e) {
        log.warn("[ModelRouter] Agent 异常，fallback 到 DashScope 单轮: {}", e.getMessage());
      }
    }

    // Step 4: fallback / 非 general intent → 原 DashScope 单轮逻辑
    String contextBlock = buildContextBlock();
    String systemPrompt = buildSystemPrompt(intent, scene)
        + "\n请只返回 JSON，不要输出 Markdown。固定结构为："
        + "{\"answer\":\"...\",\"suggestions\":[\"...\",\"...\",\"...\"],\"disclaimer\":\"...\"}";
    String userMessage = contextBlock + "\n问题：" + safeQuestion;

    JsonNode payload;
    if (openMedService.isConfigured() && intent.equals("medication")) {
      payload = dashScopeService.requestJson(
          systemPrompt, userMessage, dashScopeService.chatModel(), 0.35, "药物咨询");
    } else {
      payload = dashScopeService.requestJson(
          systemPrompt, userMessage, dashScopeService.chatModel(), 0.35, "健康咨询");
    }

    // Step 5: Restore PII in answer
    String answer = payload.path("answer").asText("");
    answer = piiScrubService.restore(answer, scrubResult.masks());

    // Build response in expected format
    try {
      var node = objectMapper.createObjectNode();
      node.put("answer", answer);
      var arr = node.putArray("suggestions");
      JsonNode suggestionsNode = payload.path("suggestions");
      if (suggestionsNode.isArray()) {
        for (JsonNode s : suggestionsNode) arr.add(s.asText(""));
      }
      node.put("disclaimer", payload.path("disclaimer").asText("仅用于健康管理辅助。"));
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      return "{\"answer\":\"" + escapeJson(answer) + "\",\"suggestions\":[],\"disclaimer\":\"仅用于健康管理辅助。\"}";
    }
  }

  /**
   * 规范化 Agent 返回的 JSON 字符串：去除 Markdown 代码块、提取 {answer,suggestions,disclaimer}。
   *
   * <p>LLM（kimi-k2.5）有时返回 {@code ```json ... ``` } 包裹的 JSON，需容错提取并重新序列化为标准 JSON。
   *
   * @return 标准 JSON 字符串；解析失败返回 null
   */
  private String normalizeAgentJson(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String text = raw.trim();
    // 去除 Markdown 代码块
    if (text.startsWith("```")) {
      int firstNewline = text.indexOf('\n');
      if (firstNewline > 0) {
        text = text.substring(firstNewline + 1);
      }
      if (text.endsWith("```")) {
        text = text.substring(0, text.length() - 3);
      }
      text = text.trim();
    }
    try {
      JsonNode parsed = objectMapper.readTree(text);
      if (parsed == null || parsed.isMissingNode()) return null;
      // 重新序列化为标准 JSON（保证下游 readTree 一致）
      return objectMapper.writeValueAsString(parsed);
    } catch (Exception e) {
      log.warn("[ModelRouter] Agent JSON 解析失败: {}", e.getMessage());
      return null;
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
    // 从 PromptTemplateService 加载 base + 专项提示词
    String base = promptTemplateService.render("consult.router_base", java.util.Map.of());
    String suffix = switch (intent) {
      case "medication" -> promptTemplateService.render("consult.router_medication", java.util.Map.of());
      case "blood_pressure" -> promptTemplateService.render("consult.router_blood_pressure", java.util.Map.of());
      case "sleep" -> promptTemplateService.render("consult.router_sleep", java.util.Map.of());
      case "exercise" -> promptTemplateService.render("consult.router_exercise", java.util.Map.of());
      default -> promptTemplateService.render("consult.router_general", java.util.Map.of());
    };
    return base + suffix;
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
