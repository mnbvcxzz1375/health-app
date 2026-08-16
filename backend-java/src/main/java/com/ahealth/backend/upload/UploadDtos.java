package com.ahealth.backend.upload;

import com.ahealth.backend.ai.AiDtos;
import com.ahealth.backend.rehab.RehabDtos;
import java.util.List;

public final class UploadDtos {
  private UploadDtos() {}

  public record AnalyzeTaskResponse(String taskId) {}

  /**
   * 自定义模型分析响应：
   * - type="bone"：boneAgeResult 必填，analyzeReport 为 null
   * - 其他类型：analyzeReport 必填，boneAgeResult 为 null；source 用于区分结构化 LLM fallback
   */
  public record CustomModelTaskResponse(
      String taskId,
      String type,            // "bone" | ...
      String source,          // "local_model" | "llm_fallback" | ...
      AiDtos.BoneAgeResult boneAgeResult,
      AnalyzeReport analyzeReport
  ) {}

  public record AnalyzeReport(
      String title,
      String summary,
      String riskLevel,
      List<String> points,
      List<String> advice,
      String rehabFocus,
      List<String> followUp,
      String caution
  ) {}

  public record AnalyzeResultResponse(
      String status,
      List<String> points,
      List<String> advice,
      AnalyzeReport report,
      Boolean saved,
      String message
  ) {}

  public record SavedAnalyzeReport(
      String taskId,
      String type,
      String fileName,
      String createdAt,
      String updatedAt,
      AnalyzeReport report
  ) {}

  public record AnalyzeSaveResponse(
      boolean success,
      boolean saved,
      RehabDtos.RehabPlanDraft rehabPlanDraft
  ) {}
}
