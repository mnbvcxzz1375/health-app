package com.ahealth.backend.ai;

import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j LLM 客户端配置。
 *
 * <p>暴露 {@link OpenAiChatModel} Bean，用于 Agent 框架（{@code AiServices}）的 Function Calling。
 * 配置从 application.yml 的 langchain4j.open-ai.chat-model.* 读取。
 *
 * <p>如果 langchain4j-open-ai-spring-boot-starter 已自动配置 OpenAiChatModel，
 * 本配置的 @ConditionalOnMissingBean 会自动让位。
 */
@Configuration
public class LlmClients {

  @Bean
  @ConditionalOnMissingBean(OpenAiChatModel.class)
  public OpenAiChatModel openAiChatModel(
      @Value("${langchain4j.open-ai.chat-model.api-key:${DASHSCOPE_API_KEY:${QWEN_API_KEY:}}}") String apiKey,
      @Value("${langchain4j.open-ai.chat-model.base-url:https://coding.dashscope.aliyuncs.com/v1}") String baseUrl,
      @Value("${langchain4j.open-ai.chat-model.model-name:kimi-k2.5}") String modelName,
      @Value("${langchain4j.open-ai.chat-model.temperature:0.3}") double temperature,
      @Value("${langchain4j.open-ai.chat-model.timeout:PT180S}") Duration timeout
  ) {
    return OpenAiChatModel.builder()
        .apiKey(apiKey)
        .baseUrl(baseUrl)
        .modelName(modelName)
        .temperature(temperature)
        .timeout(timeout)
        .build();
  }
}
