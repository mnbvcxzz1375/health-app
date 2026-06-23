package com.ahealth.backend.rehab;

import java.util.List;

public final class RehabDtos {
  private RehabDtos() {}

  public record RehabExercise(
      long id,
      String name,
      String category,
      String duration,
      String level,
      int minutes,
      List<String> steps,
      String caution,
      String focus,
      List<String> benefits,
      int videoMinutes,
      boolean done
  ) {}

  public record RehabWeekTrend(
      List<String> labels,
      List<Integer> values,
      String insight,
      int deltaPercent
  ) {}

  public record RehabPlanSummary(
      String focus,
      String frequency,
      String duration,
      String intensity
  ) {}

  public record RehabReminderSummary(
      String time,
      String days,
      String channel,
      String status
  ) {}

  public record RehabPlanResponse(
      String label,
      List<RehabExercise> exercises,
      RehabWeekTrend weekTrend,
      RehabPlanSummary planSummary,
      RehabReminderSummary reminderSummary
  ) {}

  public record RehabReminderResponse(
      String name,
      String time,
      List<String> days,
      boolean pushEnabled
  ) {}

  public record SaveRehabReminderRequest(
      String name,
      String time,
      List<String> days,
      Boolean pushEnabled
  ) {}

  public record RehabPlanSettingsResponse(
      String focus,
      String frequency,
      String duration,
      String intensity
  ) {}

  public record PlanReminderDraft(
      String time,
      List<String> days,
      boolean pushEnabled
  ) {}

  public record DraftExerciseCandidate(
      String mode,
      String name,
      String category,
      String duration,
      String level,
      int minutes,
      List<String> steps,
      String caution,
      String focus,
      List<String> benefits,
      int videoMinutes
  ) {}

  public record RehabPlanDraft(
      List<String> sourceTaskIds,
      RehabPlanSummary summary,
      List<DraftExerciseCandidate> exercises,
      PlanReminderDraft reminder
  ) {}

  /** Device-based rehab performance analysis */
  public record RehabPerformanceAnalysis(
      String date,
      List<ExerciseAnalysis> exerciseAnalyses,
      String overallAssessment,
      List<String> warnings,
      List<String> planAdjustments
  ) {}

  public record ExerciseAnalysis(
      String exerciseName,
      String performanceLevel,   // "excellent", "good", "overexertion", "underperformance", "no_device_data", "not_completed"
      double avgHeartRate,
      double maxHeartRate,
      int actualDurationSeconds,
      int targetDurationSeconds,
      double exertionScore,      // 0-1, >0.8 = overexertion risk
      String note
  ) {}
}
