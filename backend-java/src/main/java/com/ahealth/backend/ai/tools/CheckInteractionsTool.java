package com.ahealth.backend.ai.tools;

import com.ahealth.backend.ai.AgentToolCallLogger;
import com.ahealth.backend.knowledge.InteractionCheckService;
import com.ahealth.backend.knowledge.KnowledgeDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent 工具：检查用户当前用药清单的相互作用（中西药、药食、DDI、过敏、禁忌人群）。
 *
 * <p>依赖 CurrentUser — ConsultAgent 在调用 Agent 前会临时切换 SecurityContext。
 *
 * <p>Step 28 新增：调用前后通过 AgentToolCallLogger 记录到 agent_tool_calls 表。
 */
@Component
public class CheckInteractionsTool {

  private static final Logger log = LoggerFactory.getLogger(CheckInteractionsTool.class);
  private static final String TOOL_NAME = "checkInteractions";

  private final InteractionCheckService interactionCheckService;
  private final ObjectMapper objectMapper;
  private final AgentToolCallLogger agentLogger;

  public CheckInteractionsTool(
      InteractionCheckService interactionCheckService,
      ObjectMapper objectMapper,
      AgentToolCallLogger agentLogger
  ) {
    this.interactionCheckService = interactionCheckService;
    this.objectMapper = objectMapper;
    this.agentLogger = agentLogger;
  }

  @Tool("检查当前登录用户的所有用药相互作用告警，返回 6 类报告：十八反十九畏/中西药交互/药食相互作用/DDI 警告/过敏冲突/禁忌人群警告")
  String checkInteractions() {
    long start = System.currentTimeMillis();
    String inputSummary = "checkAll|userId=currentUser";
    try {
      KnowledgeDtos.InteractionReport report = interactionCheckService.checkAll();
      String json = objectMapper.writeValueAsString(report);
      log.debug("[Tool:{}] totalWarnings={}", TOOL_NAME, report.totalWarnings());
      agentLogger.recordToolCall(TOOL_NAME, inputSummary,
          "totalWarnings=" + report.totalWarnings() + "|json=" + truncate(json, 2000),
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
