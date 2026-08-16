package com.ahealth.backend.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Agent 工具调用日志记录器：用 ThreadLocal 跟踪单次 Agent 会话的工具调用链。
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@code startSession(scene, userId)} 在 Agent 入口调用，生成 sessionId</li>
 *   <li>{@code recordToolCall(...)} 由 LangChain4j 工具调用钩子触发（或手动调用）</li>
 *   <li>{@code endSession()} 在 Agent 出口调用，清理 ThreadLocal</li>
 *   <li>所有方法 try-catch，失败仅 WARN 不抛异常（避免影响 Agent 主流程）</li>
 * </ul>
 *
 * <p>数据持久化到 {@code agent_tool_calls} 表，供 {@code /api/admin/agent/*} 端点查询。
 */
@Service
public class AgentToolCallLogger {

  private static final Logger log = LoggerFactory.getLogger(AgentToolCallLogger.class);

  private final JdbcTemplate jdbc;

  // ThreadLocal 持有当前 Agent 会话状态
  private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();
  private static final ThreadLocal<Integer> CURRENT_ITERATION = new ThreadLocal<>();
  private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();
  private static final ThreadLocal<String> CURRENT_SCENE = new ThreadLocal<>();

  public AgentToolCallLogger(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 在 Agent 入口调用，生成新 sessionId 并重置 iteration。 */
  public void startSession(String scene, long userId) {
    try {
      String sessionId = "agent_" + UUID.randomUUID().toString().replace("-", "");
      CURRENT_SESSION_ID.set(sessionId);
      CURRENT_ITERATION.set(0);
      CURRENT_USER_ID.set(userId);
      CURRENT_SCENE.set(scene == null ? "consult" : scene);
      log.debug("[AgentLogger] session started id={} scene={} userId={}", sessionId, scene, userId);
    } catch (Exception e) {
      log.warn("[AgentLogger] startSession 异常: {}", e.getMessage());
    }
  }

  /** 记录一次工具调用。iteration 自增。 */
  public void recordToolCall(String toolName, String input, String output, long durationMs) {
    try {
      String sessionId = CURRENT_SESSION_ID.get();
      if (sessionId == null) {
        log.debug("[AgentLogger] recordToolCall called without active session, skip");
        return;
      }
      Long userId = CURRENT_USER_ID.get();
      String scene = CURRENT_SCENE.get();
      int iteration = CURRENT_ITERATION.get() == null ? 0 : CURRENT_ITERATION.get();

      // 截断超长 input/output（避免 DB 字段过长）
      String trimmedInput = truncate(input, 4000);
      String trimmedOutput = truncate(output, 8000);

      jdbc.update(
          """
          INSERT INTO agent_tool_calls (session_id, user_id, scene, tool_name, tool_input, tool_output, iteration, duration_ms, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
          """,
          sessionId, userId, scene, toolName, trimmedInput, trimmedOutput, iteration, durationMs
      );

      CURRENT_ITERATION.set(iteration + 1);
      log.debug("[AgentLogger] tool call recorded session={} tool={} iter={} dur={}ms",
          sessionId, toolName, iteration, durationMs);
    } catch (Exception e) {
      log.warn("[AgentLogger] recordToolCall 异常: {}", e.getMessage());
    }
  }

  /** 在 Agent 出口调用，清理 ThreadLocal。 */
  public void endSession() {
    try {
      String sessionId = CURRENT_SESSION_ID.get();
      log.debug("[AgentLogger] session ended id={}", sessionId);
    } catch (Exception e) {
      log.warn("[AgentLogger] endSession 异常: {}", e.getMessage());
    } finally {
      CURRENT_SESSION_ID.remove();
      CURRENT_ITERATION.remove();
      CURRENT_USER_ID.remove();
      CURRENT_SCENE.remove();
    }
  }

  /** 查询单次会话所有工具调用记录（按 iteration 升序）。 */
  public List<Map<String, Object>> listBySession(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) return List.of();
    try {
      return jdbc.queryForList(
          "SELECT id, session_id, user_id, scene, tool_name, tool_input, tool_output, iteration, duration_ms, created_at "
              + "FROM agent_tool_calls WHERE session_id=? ORDER BY iteration ASC, id ASC",
          sessionId);
    } catch (Exception e) {
      log.warn("[AgentLogger] listBySession 异常: {}", e.getMessage());
      return List.of();
    }
  }

  /** 查询最近 N 条工具调用记录。 */
  public List<Map<String, Object>> listRecent(String scene, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 100));
    try {
      if (scene == null || scene.isBlank()) {
        return jdbc.queryForList(
            "SELECT id, session_id, user_id, scene, tool_name, tool_input, tool_output, iteration, duration_ms, created_at "
                + "FROM agent_tool_calls ORDER BY created_at DESC LIMIT ?",
            safeLimit);
      }
      return jdbc.queryForList(
          "SELECT id, session_id, user_id, scene, tool_name, tool_input, tool_output, iteration, duration_ms, created_at "
              + "FROM agent_tool_calls WHERE scene=? ORDER BY created_at DESC LIMIT ?",
          scene, safeLimit);
    } catch (Exception e) {
      log.warn("[AgentLogger] listRecent 异常: {}", e.getMessage());
      return List.of();
    }
  }

  /** 查询当前线程的 sessionId（供调试用）。 */
  public String currentSessionId() {
    return CURRENT_SESSION_ID.get();
  }

  private String truncate(String s, int maxLen) {
    if (s == null) return "";
    return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...(truncated)";
  }
}
