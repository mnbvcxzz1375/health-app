package com.ahealth.backend.upload;

import com.ahealth.backend.rehab.RehabDtos;
import java.util.List;

public final class UploadDtos {
  private UploadDtos() {}

  public record AnalyzeTaskResponse(String taskId) {}

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
