package com.ahealth.backend.ai.tools;

import com.ahealth.backend.ai.AgentToolCallLogger;
import com.ahealth.backend.diet.FoodService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent 工具：按关键词搜索食材库，返回营养成分（卡路里/蛋白质/脂肪/碳水/纤维/升糖指数）。
 *
 * <p>用于饮食建议、热量计算、食物禁忌查询等场景。
 *
 * <p>Step 28 新增：调用前后通过 AgentToolCallLogger 记录到 agent_tool_calls 表。
 */
@Component
public class SearchFoodsTool {

  private static final Logger log = LoggerFactory.getLogger(SearchFoodsTool.class);
  private static final String TOOL_NAME = "searchFoods";

  private final FoodService foodService;
  private final ObjectMapper objectMapper;
  private final AgentToolCallLogger agentLogger;

  public SearchFoodsTool(
      FoodService foodService,
      ObjectMapper objectMapper,
      AgentToolCallLogger agentLogger
  ) {
    this.foodService = foodService;
    this.objectMapper = objectMapper;
    this.agentLogger = agentLogger;
  }

  @Tool("按关键词搜索食材库，返回营养成分（卡路里/蛋白质/脂肪/碳水/纤维/升糖指数）。用于饮食建议、热量计算、食物禁忌查询。")
  String searchFoods(
      @P("食材关键词，如：苹果、鸡胸肉、燕麦") String keyword,
      @P("返回条数，建议 5-10") int limit
  ) {
    long start = System.currentTimeMillis();
    String inputSummary = "keyword=" + truncate(keyword, 100) + "|limit=" + limit;
    try {
      int k = limit <= 0 ? 5 : Math.min(limit, 20);
      String kw = keyword == null ? "" : keyword.trim();
      var items = foodService.searchFoods(kw, k);
      String json = objectMapper.writeValueAsString(items);
      log.debug("[Tool:{}] keyword='{}' limit={} hits={}", TOOL_NAME, kw, k, items.size());
      agentLogger.recordToolCall(TOOL_NAME, inputSummary,
          "hits=" + items.size() + "|json=" + truncate(json, 2000),
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
