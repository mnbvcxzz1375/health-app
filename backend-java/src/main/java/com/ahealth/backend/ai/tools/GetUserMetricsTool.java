package com.ahealth.backend.ai.tools;

import com.ahealth.backend.ai.AgentToolCallLogger;
import com.ahealth.backend.context.ContextDtos;
import com.ahealth.backend.context.ContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent 工具：获取当前登录用户的健康指标快照。
 *
 * <p>包含：用户画像、今日监测、当前关注、当前用药、历史记忆、健康基线、用药摘要、交互记忆。
 *
 * <p>依赖 CurrentUser — ConsultAgent 在调用 Agent 前会临时切换 SecurityContext。
 *
 * <p>Step 28 新增：调用前后通过 AgentToolCallLogger 记录到 agent_tool_calls 表。
 */
@Component
public class GetUserMetricsTool {

  private static final Logger log = LoggerFactory.getLogger(GetUserMetricsTool.class);
  private static final String TOOL_NAME = "getUserMetrics";

  private final ContextService contextService;
  private final ObjectMapper objectMapper;
  private final AgentToolCallLogger agentLogger;

  public GetUserMetricsTool(
      ContextService contextService,
      ObjectMapper objectMapper,
      AgentToolCallLogger agentLogger
  ) {
    this.contextService = contextService;
    this.objectMapper = objectMapper;
    this.agentLogger = agentLogger;
  }

  @Tool("获取当前登录用户的健康指标快照：画像/今日监测/当前用药/历史记忆/健康基线")
  String getUserMetrics() {
    long start = System.currentTimeMillis();
    String inputSummary = "snapshot|userId=currentUser";
    try {
      ContextDtos.ContextSnapshot snapshot = contextService.getSnapshot();
      String json = objectMapper.writeValueAsString(snapshot);
      int medCount = snapshot.currentMedications() == null ? 0 : snapshot.currentMedications().size();
      log.debug("[Tool:{}] medications={}", TOOL_NAME, medCount);
      agentLogger.recordToolCall(TOOL_NAME, inputSummary,
          "medications=" + medCount + "|json=" + truncate(json, 2000),
          System.currentTimeMillis() - start);
      return json;
    } catch (Exception e) {
      log.warn("[Tool:{}] 异常: {}", TOOL_NAME, e.getMessage());
      agentLogger.recordToolCall(TOOL_NAME, inputSummary,
          "error=" + e.getMessage(), System.currentTimeMillis() - start);
      return "{\"error\":\"" + e.getMessage() + "\"}";
    }
  }

  private static String truncate(String s, int maxLen) {
    if (s == null) return "";
    return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
  }
}
