package com.ahealth.backend.ai;

import java.util.List;
import java.util.Map;
import com.ahealth.backend.security.AdminAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 工具调用审计管理端点：查询 LangChain4j ReAct loop 的工具调用链。
 *
 * <p>用于运维与调优：
 * <ul>
 *   <li>{@code GET /api/admin/agent/sessions/{sessionId}} — 单次会话所有工具调用记录</li>
 *   <li>{@code GET /api/admin/agent/recent?scene=&limit=} — 最近 N 条工具调用记录</li>
 * </ul>
 *
 * <p>鉴权：所有端点都需要管理员用户。
 */
@RestController
@RequestMapping("/api/admin/agent")
public class AgentAdminController {

  private final AgentToolCallLogger agentToolCallLogger;
  private final AdminAccessService adminAccessService;

  public AgentAdminController(AgentToolCallLogger agentToolCallLogger,
      AdminAccessService adminAccessService) {
    this.agentToolCallLogger = agentToolCallLogger;
    this.adminAccessService = adminAccessService;
  }

  /** 查询指定 Agent 会话的所有工具调用记录（按 iteration 升序）。 */
  @GetMapping("/sessions/{sessionId}")
  public List<Map<String, Object>> session(@PathVariable String sessionId) {
    adminAccessService.requireAdmin();
    return agentToolCallLogger.listBySession(sessionId);
  }

  /** 查询最近 N 条工具调用记录，可按 scene 过滤。 */
  @GetMapping("/recent")
  public List<Map<String, Object>> recent(
      @RequestParam(required = false) String scene,
      @RequestParam(defaultValue = "20") int limit) {
      adminAccessService.requireAdmin();
    return agentToolCallLogger.listRecent(scene, Math.min(Math.max(limit, 1), 100));
  }
}
