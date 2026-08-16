package com.ahealth.backend.ai.tools;

import com.ahealth.backend.ai.AgentToolCallLogger;
import com.ahealth.backend.rag.RagDtos;
import com.ahealth.backend.rag.RagSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent 工具：从健康知识库 RAG 检索文档片段。
 *
 * <p>LangChain4j 通过 @Tool 注解自动注册到 Agent 上下文，LLM 可主动调用以获取相关知识。
 *
 * <p>Step 28 新增：调用前后通过 AgentToolCallLogger 记录到 agent_tool_calls 表，
 * 供 /api/admin/agent/* 端点查询。ThreadLocal sessionId 由 ConsultAgent.startSession 设置。
 */
@Component
public class SearchKnowledgeTool {

  private static final Logger log = LoggerFactory.getLogger(SearchKnowledgeTool.class);
  private static final String TOOL_NAME = "searchKnowledge";

  private final RagSearchService ragSearchService;
  private final ObjectMapper objectMapper;
  private final AgentToolCallLogger agentLogger;

  public SearchKnowledgeTool(
      RagSearchService ragSearchService,
      ObjectMapper objectMapper,
      AgentToolCallLogger agentLogger
  ) {
    this.ragSearchService = ragSearchService;
    this.objectMapper = objectMapper;
    this.agentLogger = agentLogger;
  }

  @Tool("从健康知识库检索与查询相关的文档片段，返回 top-K 结果。docType 可选：consult_qa/herb_guide/drug_label/rehab_guide/food_guide/ddi_rule；为空则查全部类型")
  String searchKnowledge(
      @P("查询文本，例如：阿司匹林副作用、低血压饮食建议") String query,
      @P("文档类型过滤，可空字符串") String docType,
      @P("返回条数，建议 3-5") int topK
  ) {
    long start = System.currentTimeMillis();
    String inputSummary = "query=" + truncate(query, 100) + "|docType=" + docType + "|topK=" + topK;
    try {
      int k = topK <= 0 ? 5 : Math.min(topK, 10);
      String type = (docType == null || docType.isBlank()) ? null : docType.trim();
      List<RagDtos.RagSearchHit> hits = ragSearchService.search(query, type, k);
      String json = objectMapper.writeValueAsString(hits);
      log.debug("[Tool:{}] query='{}' docType={} hits={}", TOOL_NAME, query, type, hits.size());
      agentLogger.recordToolCall(TOOL_NAME, inputSummary,
          "hits=" + hits.size() + "|json=" + truncate(json, 2000),
          System.currentTimeMillis() - start);
      return json;
    } catch (Exception e) {
      log.warn("[Tool:{}] 异常: {}", TOOL_NAME, e.getMessage());
      agentLogger.recordToolCall(TOOL_NAME, inputSummary,
          "error=" + e.getMessage(), System.currentTimeMillis() - start);
      return "[]";
    }
  }

  private static String truncate(String s, int maxLen) {
    if (s == null) return "";
    return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
  }
}
