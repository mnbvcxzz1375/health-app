package com.ahealth.backend.consult;

import com.ahealth.backend.ai.AgentToolCallLogger;
import com.ahealth.backend.ai.PromptTemplateService;
import com.ahealth.backend.ai.tools.CheckInteractionsTool;
import com.ahealth.backend.ai.tools.GetUserMetricsTool;
import com.ahealth.backend.ai.tools.SearchFoodsTool;
import com.ahealth.backend.ai.tools.SearchKnowledgeTool;
import com.ahealth.backend.context.ContextService;
import com.ahealth.backend.security.AuthenticatedUser;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 健康咨询 Agent：用 LangChain4j ReAct loop 串联 4 个工具实现"检索增强生成 + 多轮推理"。
 *
 * <p>核心能力：
 * <ul>
 *   <li>{@link SearchKnowledgeTool} — 从 RAG 知识库检索文档片段</li>
 *   <li>{@link CheckInteractionsTool} — 查询当前用户的药物相互作用</li>
 *   <li>{@link GetUserMetricsTool} — 获取用户健康画像与监测指标</li>
 *   <li>{@link SearchFoodsTool} — 搜索食材库营养成分</li>
 * </ul>
 *
 * <p>失败降级：Agent 抛异常或调用超时则返回 null，由 {@link ConsultService} fallback 到 DashScope 单轮。
 *
 * <p>上下文切换：因 {@link ContextService#getSnapshot()} 内部依赖 {@code CurrentUser.requireUserId()}
 * 且无 userId 重载，本类在 Agent 执行期间临时切换 SecurityContext 注入 AuthenticatedUser，finally 恢复。
 */
@Service
public class ConsultAgent {

  private static final Logger log = LoggerFactory.getLogger(ConsultAgent.class);

  private final OpenAiChatModel chatModel;
  private final SearchKnowledgeTool searchKnowledgeTool;
  private final CheckInteractionsTool checkInteractionsTool;
  private final GetUserMetricsTool getUserMetricsTool;
  private final SearchFoodsTool searchFoodsTool;
  private final PromptTemplateService promptTemplateService;
  private final ContextService contextService;
  private final AgentToolCallLogger agentLogger;

  public ConsultAgent(
      OpenAiChatModel chatModel,
      SearchKnowledgeTool searchKnowledgeTool,
      CheckInteractionsTool checkInteractionsTool,
      GetUserMetricsTool getUserMetricsTool,
      SearchFoodsTool searchFoodsTool,
      PromptTemplateService promptTemplateService,
      ContextService contextService,
      @Lazy AgentToolCallLogger agentLogger
  ) {
    this.chatModel = chatModel;
    this.searchKnowledgeTool = searchKnowledgeTool;
    this.checkInteractionsTool = checkInteractionsTool;
    this.getUserMetricsTool = getUserMetricsTool;
    this.searchFoodsTool = searchFoodsTool;
    this.promptTemplateService = promptTemplateService;
    this.contextService = contextService;
    this.agentLogger = agentLogger;
  }

  /** LangChain4j Agent 接口：单方法 ask，由 AiServices 代理生成实现。 */
  interface HealthAssistant {
    String ask(@UserMessage String question);
  }

  /**
   * Agent 主入口：构造 ReAct Agent 并执行用户问题。
   *
   * @param question 用户问题（已注入 RAG 知识片段，由 ConsultService 拼接）
   * @param userId   当前用户 ID（用于 SecurityContext 切换 + 工具内 CurrentUser 调用）
   * @return LLM 返回的 JSON 文本（含 {answer, suggestions, disclaimer}），失败返回 null
   */
  public String ask(String question, long userId) {
    agentLogger.startSession("consult", userId);
    Authentication prevAuth = SecurityContextHolder.getContext().getAuthentication();
    try {
      SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken(
              new AuthenticatedUser(userId, "", "", ""),
              "",
              AuthorityUtils.NO_AUTHORITIES));

      HealthAssistant agent = AiServices.builder(HealthAssistant.class)
          .chatLanguageModel(chatModel)
          .tools(searchKnowledgeTool, checkInteractionsTool, getUserMetricsTool, searchFoodsTool)
          .systemMessageProvider(memoryId -> renderSystemPrompt(userId))
          .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
          .build();

      return agent.ask(question);
    } catch (Exception e) {
      log.warn("[ConsultAgent] Agent loop failed, will fallback to DashScope: {}", e.getMessage());
      return null;
    } finally {
      SecurityContextHolder.getContext().setAuthentication(prevAuth);
      agentLogger.endSession();
    }
  }

  /** 渲染 consult.agent_system 系统提示词，注入用户上下文摘要。 */
  private String renderSystemPrompt(long userId) {
    String userContext = safeSystemSummary();
    return promptTemplateService.render("consult.agent_system", Map.of("user_context", userContext));
  }

  /** 获取用户画像摘要，失败返回空字符串（不影响 Agent 启动）。 */
  private String safeSystemSummary() {
    try {
      String summary = contextService.getSnapshot().systemSummary();
      return summary == null ? "" : summary;
    } catch (Exception e) {
      log.debug("[ConsultAgent] getSnapshot 失败，user_context 置空: {}", e.getMessage());
      return "";
    }
  }
}
