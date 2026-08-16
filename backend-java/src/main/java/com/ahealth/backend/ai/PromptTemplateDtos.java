package com.ahealth.backend.ai;

import java.time.LocalDateTime;
import java.util.List;

/** Prompt 模板管理 API 的 DTOs。 */
public final class PromptTemplateDtos {

  private PromptTemplateDtos() {}

  /** 模板视图（列表/详情）。 */
  public record PromptTemplateView(
      int id,
      String templateKey,
      String scene,
      String content,
      List<String> variables,
      int version,
      boolean isActive,
      String description,
      LocalDateTime createdAt) {

    public static PromptTemplateView from(PromptTemplateService.PromptTemplate t) {
      return new PromptTemplateView(
          t.id(),
          t.templateKey(),
          t.scene(),
          t.content(),
          t.variables(),
          t.version(),
          t.isActive(),
          t.description(),
          t.createdAt());
    }
  }

  /** 创建/更新模板请求。 */
  public record UpsertPromptRequest(
      String templateKey,
      String scene,
      String content,
      List<String> variables,
      String description) {}

  /** 列表响应。 */
  public record ListResponse(List<PromptTemplateView> items, int total) {}

  /** 单个模板响应。 */
  public record SingleResponse(PromptTemplateView template) {}
}
