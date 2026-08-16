package com.ahealth.backend.ai;

import java.util.List;

/** 答案模板 admin 端点 DTOs。 */
public final class AnswerTemplateDtos {

  private AnswerTemplateDtos() {}

  /** upsert 请求体。 */
  public record UpsertRequest(
      String templateKey,
      String scene,
      String category,
      List<String> keywords,
      String pattern,
      String templateText,
      List<String> variables,
      Integer priority) {

    public UpsertRequest {
      if (templateKey == null || templateKey.isBlank()) {
        throw new IllegalArgumentException("templateKey 不能为空");
      }
      if (templateText == null || templateText.isBlank()) {
        throw new IllegalArgumentException("templateText 不能为空");
      }
      if (scene == null || scene.isBlank()) scene = "consult";
      if (priority == null) priority = 0;
    }
  }

  /** upsert 响应。 */
  public record UpsertResponse(int newVersion, String templateKey, String message) {}

  /** 测试匹配响应。 */
  public record TestResponse(
      boolean matched,
      String templateKey,
      String scene,
      String confidence,
      String renderedText) {}
}
