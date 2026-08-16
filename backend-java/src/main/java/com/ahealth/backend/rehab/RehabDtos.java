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

  // ===== 智能计划 =====

  public record SmartPlanRequest(
      double height,
      double weight,
      int age,
      String gender,
      String goal,
      String activityLevel,
      String source
  ) {
    public SmartPlanRequest(double height, double weight, int age, String gender,
        String goal, String activityLevel) {
      this(height, weight, age, gender, goal, activityLevel, "manual");
    }
  }

  public record CaseProfile(
      double height,
      double weight,
      int age,
      String gender,
      String goal,
      String activityLevel,
      String inputSource
  ) {}

  public record CaseMonitoring(
      int restingHeartRate,
      double sleepScore,
      double stressScore,
      double vo2Max,
      int averageSteps,
      int riskScore,
      String riskLevel
  ) {}

  public record CaseMedication(
      int activeCount,
      List<String> names,
      List<String> warnings
  ) {}

  public record CaseReport(
      String taskId,
      String type,
      String title,
      String riskLevel,
      String updatedAt
  ) {}

  public record CaseEvidence(
      String id,
      String sourceType,
      String summary,
      String observedAt
  ) {}

  public record CaseTimeRange(
      String label,
      String from,
      String to
  ) {}

  /** Posture evidence is optional because the inference service is a separate runtime. */
  public record CasePosture(
      String status,
      Double score,
      List<String> issues,
      String source,
      String observedAt
  ) {
    public CasePosture {
      issues = issues == null ? List.of() : List.copyOf(issues);
    }
  }

  public record PlanConstraint(
      String code,
      String level,
      String reason,
      String action
  ) {}

  public record PlanSafety(
      String level,
      List<String> flags,
      String uncertainty,
      String escalation,
      List<String> actionTags
  ) {
    public PlanSafety {
      flags = flags == null ? List.of() : List.copyOf(flags);
      actionTags = actionTags == null ? List.of() : List.copyOf(actionTags);
    }

    public PlanSafety(String level, List<String> flags, String uncertainty, String escalation) {
      this(level, flags, uncertainty, escalation, List.of());
    }
  }

  public record RehabCase(
      String caseId,
      String generatedAt,
      String version,
      CaseProfile profile,
      CaseMonitoring monitoring,
      CaseMedication medication,
      List<CaseReport> reports,
      List<CaseEvidence> evidence,
      List<PlanConstraint> constraints,
      PlanSafety safety,
      CaseTimeRange timeRange,
      CasePosture posture
  ) {
    public RehabCase(String caseId, String generatedAt, String version, CaseProfile profile,
        CaseMonitoring monitoring, CaseMedication medication, List<CaseReport> reports,
        List<CaseEvidence> evidence, List<PlanConstraint> constraints, PlanSafety safety) {
      this(caseId, generatedAt, version, profile, monitoring, medication, reports, evidence,
          constraints, safety, new CaseTimeRange("rolling_30_days", "", generatedAt),
          new CasePosture("not_available", null, List.of(), "posture-inference-service", ""));
    }
  }

  public record SmartPlanResponse(
      double bmi,
      String bmiCategory,
      double bmr,
      double tdee,
      double targetCalories,
      List<Long> exerciseIds,
      List<ExerciseBrief> exercises,
      List<WeeklyDayPlan> weeklyPlan,
      DietSuggestion dietSuggestion,
      String summary,
      RehabCase rehabCase
  ) {
    public SmartPlanResponse(double bmi, String bmiCategory, double bmr, double tdee,
        double targetCalories, List<Long> exerciseIds, List<ExerciseBrief> exercises) {
      this(bmi, bmiCategory, bmr, tdee, targetCalories, exerciseIds, exercises,
          List.of(), new DietSuggestion(targetCalories, 0, 0, 0, List.of(), List.of()), "", null);
    }
  }

  public record ExerciseBrief(
      long id,
      String name,
      String goalType,
      String muscleGroup,
      Integer caloriesBurnPerMin,
      String equipment,
      String steps,
      String benefits,
      String impact,
      int minutes
  ) {
    public ExerciseBrief(long id, String name, String goalType, String muscleGroup,
        Integer caloriesBurnPerMin, String equipment) {
      this(id, name, goalType, muscleGroup, caloriesBurnPerMin, equipment, "", "", "low", 4);
    }
  }

  public record WeeklyDayPlan(
      String day,
      int dayIndex,
      boolean isRestDay,
      String focus,
      List<ExerciseBrief> exercises,
      int duration,
      int estimatedCalories
  ) {}

  public record DietSuggestionMeal(
      String mealType,
      String title,
      List<String> foods,
      int calories,
      int protein,
      int carbs,
      int fat
  ) {}

  public record DietSuggestion(
      double targetCalories,
      int targetProtein,
      int targetCarbs,
      int targetFat,
      List<DietSuggestionMeal> meals,
      List<String> tips
  ) {}

  public record BodyMetrics(
      double bmi,
      String bmiCategory,
      double bmr,
      double tdee,
      double targetCalories
  ) {}
}
