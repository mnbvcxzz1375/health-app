package com.ahealth.backend.rehab;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 智能康复计划生成。
 * 基于身高/体重/年龄/性别/目标计算 BMI/BMR/TDEE，并从 rehab_exercises 表筛选匹配动作。
 * 公式：Mifflin-St Jeor BMR + TDEE 活动系数 + 目标热量调整。
 */
@Service
public class SmartRehabPlannerService {
  private final JdbcTemplate jdbcTemplate;
  private final RehabCaseService rehabCaseService;

  public SmartRehabPlannerService(JdbcTemplate jdbcTemplate, RehabCaseService rehabCaseService) {
    this.jdbcTemplate = jdbcTemplate;
    this.rehabCaseService = rehabCaseService;
  }

  public RehabDtos.SmartPlanResponse generatePlan(RehabDtos.SmartPlanRequest req) {
    double heightCm = req.height() <= 0 ? 0 : req.height();
    double weightKg = req.weight() <= 0 ? 0 : req.weight();
    int age = req.age();
    String gender = normalizeGender(req.gender());
    String goal = normalizeGoal(req.goal());
    String activityLevel = normalizeActivity(req.activityLevel());
    RehabDtos.RehabCase rehabCase = rehabCaseService.buildCurrentCase(req);
    if (isConservative(rehabCase)) {
      goal = "rehab";
      activityLevel = "sedentary";
    }

    double bmi = (heightCm > 0 && weightKg > 0)
        ? weightKg / Math.pow(heightCm / 100.0, 2)
        : 0.0;
    String bmiCategory = bmiCategory(bmi);

    boolean hasBodyMetrics = heightCm > 0 && weightKg > 0 && age > 0;
    double bmr = hasBodyMetrics ? computeBmr(gender, weightKg, heightCm, age) : 0;
    double tdee = bmr > 0 ? bmr * activityFactor(activityLevel) : 0;
    double targetCalories = tdee > 0 ? targetCalories(goal, tdee) : 0;

    List<RehabDtos.ExerciseBrief> exercises = pickExercises(goal, bmiCategory, rehabCase);
    List<Long> exerciseIds = exercises.stream().map(RehabDtos.ExerciseBrief::id).toList();
    List<RehabDtos.WeeklyDayPlan> weeklyPlan = buildWeeklyPlan(exercises, rehabCase);
    RehabDtos.DietSuggestion dietSuggestion = buildDietSuggestion(targetCalories, goal);
    String summary = buildSummary(goal, activityLevel, rehabCase, exercises);

    return new RehabDtos.SmartPlanResponse(
        round(bmi),
        bmiCategory,
        Math.round(bmr),
        Math.round(tdee),
        Math.round(targetCalories),
        exerciseIds,
        exercises,
        weeklyPlan,
        dietSuggestion,
        summary,
        rehabCase
    );
  }

  /** 计算身体指标（供 RehabService.calculateBodyMetrics 使用）。 */
  public RehabDtos.BodyMetrics computeMetrics(double heightCm, double weightKg, int age, String gender, String goal, String activityLevel) {
    double bmi = (heightCm > 0 && weightKg > 0)
        ? weightKg / Math.pow(heightCm / 100.0, 2)
        : 0.0;
    boolean hasBodyMetrics = heightCm > 0 && weightKg > 0 && age > 0;
    double bmr = hasBodyMetrics
        ? computeBmr(normalizeGender(gender), weightKg, heightCm, age) : 0;
    double tdee = hasBodyMetrics ? bmr * activityFactor(normalizeActivity(activityLevel)) : 0;
    double targetCalories = hasBodyMetrics ? targetCalories(normalizeGoal(goal), tdee) : 0;
    return new RehabDtos.BodyMetrics(
        round(bmi),
        bmiCategory(bmi),
        Math.round(bmr),
        Math.round(tdee),
        Math.round(targetCalories)
    );
  }

  private double computeBmr(String gender, double weightKg, double heightCm, int age) {
    // Mifflin-St Jeor
    double base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age;
    return "female".equals(gender) ? base - 161 : base + 5;
  }

  private double activityFactor(String level) {
    return switch (level) {
      case "light" -> 1.375;
      case "moderate" -> 1.55;
      case "active" -> 1.725;
      default -> 1.2; // sedentary
    };
  }

  private double targetCalories(String goal, double tdee) {
    return switch (goal) {
      case "fat_loss" -> tdee - 500;
      case "muscle_gain" -> tdee + 300;
      default -> tdee;
    };
  }

  private int exerciseCount(String goal) {
    return switch (goal) {
      case "fat_loss" -> 5;
      case "muscle_gain" -> 4;
      case "flexibility" -> 4;
      case "rehab" -> 3;
      default -> 3;
    };
  }

  private String bmiCategory(double bmi) {
    if (bmi <= 0) return "unknown";
    if (bmi < 18.5) return "underweight";
    if (bmi < 24) return "normal";
    if (bmi < 28) return "overweight";
    return "obese";
  }

  private List<RehabDtos.ExerciseBrief> pickExercises(String goal, String bmiCategory,
      RehabDtos.RehabCase rehabCase) {
    int limit = exerciseCount(goal);
    if ("elevated".equals(rehabCase.safety().level())) limit = Math.min(limit, 2);
    if ("uncertain".equals(rehabCase.safety().level())) limit = Math.min(limit, 1);
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT id, name, category, minutes, steps_json, benefits_json "
            + "FROM rehab_exercises ORDER BY id ASC LIMIT ?", limit
    );

    List<RehabDtos.ExerciseBrief> result = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      int minutes = Math.max(1, intValue(row.get("minutes"), 4));
      result.add(new RehabDtos.ExerciseBrief(
          longValue(row.get("id")),
          stringValue(row.get("name")),
          goal,
          stringValue(row.get("category")),
          4,
          "bodyweight",
          stringValue(row.get("steps_json")),
          stringValue(row.get("benefits_json")),
          isConservative(rehabCase) ? "low" : "moderate",
          minutes
      ));
    }

    // 若筛选为空，fallback 取任意 4 条
    if (result.isEmpty()) {
      List<Map<String, Object>> fallback = jdbcTemplate.queryForList(
          "SELECT id, name, category, minutes, steps_json, benefits_json FROM rehab_exercises ORDER BY id ASC LIMIT 4"
      );
      for (Map<String, Object> row : fallback) {
        int minutes = Math.max(1, intValue(row.get("minutes"), 4));
        result.add(new RehabDtos.ExerciseBrief(
            longValue(row.get("id")),
            stringValue(row.get("name")),
            goal,
            stringValue(row.get("category")),
            4,
            "bodyweight",
            stringValue(row.get("steps_json")),
            stringValue(row.get("benefits_json")),
            isConservative(rehabCase) ? "low" : "moderate",
            minutes
        ));
      }
    }
    return result;
  }

  private List<RehabDtos.WeeklyDayPlan> buildWeeklyPlan(List<RehabDtos.ExerciseBrief> exercises,
      RehabDtos.RehabCase rehabCase) {
    List<RehabDtos.WeeklyDayPlan> plan = new ArrayList<>();
    String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    int activeDays = "elevated".equals(rehabCase.safety().level()) ? 2
        : "uncertain".equals(rehabCase.safety().level()) ? 1 : 3;
    for (int index = 0; index < days.length; index++) {
      boolean active = index < activeDays && !exercises.isEmpty();
      List<RehabDtos.ExerciseBrief> dayExercises = active ? exercises : List.of();
      int duration = active ? exercises.stream().mapToInt(item -> Math.max(1, item.minutes())).sum() : 0;
      int calories = active
          ? exercises.stream().mapToInt(item -> Math.max(1, item.minutes())
              * Math.max(1, item.caloriesBurnPerMin())).sum()
          : 0;
      plan.add(new RehabDtos.WeeklyDayPlan(days[index], index, !active,
          active ? "Conservative rehabilitation practice" : "Recovery and symptom check", dayExercises,
          duration, calories));
    }
    return plan;
  }

  private RehabDtos.DietSuggestion buildDietSuggestion(double targetCalories, String goal) {
    int calories = (int) Math.round(Math.max(0, targetCalories));
    if (calories <= 0) {
      return new RehabDtos.DietSuggestion(0, 0, 0, 0, List.of(),
          List.of("缺少完整身高、体重或年龄，暂不生成个性化热量和营养目标。",
              "补充个人资料后，再由专业人员复核饮食与康复安排。"));
    }
    int protein = "rehab".equals(goal) ? 80 : 90;
    int carbs = Math.max(0, (int) Math.round(calories * 0.45 / 4));
    int fat = Math.max(0, (int) Math.round(calories * 0.25 / 9));
    return new RehabDtos.DietSuggestion(calories, protein, carbs, fat, List.of(),
        List.of("Use this as a general nutrition target, not a prescription.",
            "If medication or disease-specific restrictions apply, confirm with a clinician or dietitian."));
  }

  private String buildSummary(String goal, String activityLevel, RehabDtos.RehabCase rehabCase,
      List<RehabDtos.ExerciseBrief> exercises) {
    String boundary = "elevated".equals(rehabCase.safety().level())
        ? "Safety constraints reduced the plan to a conservative rehabilitation starting point."
        : "uncertain".equals(rehabCase.safety().level())
            ? "Evidence is incomplete; this is a generic low-intensity starting point, not personalized medical guidance."
            : "The plan is based on the listed case evidence and should be adjusted when symptoms or monitoring change.";
    return "Goal=" + goal + ", activity=" + activityLevel + ", exercises=" + exercises.size() + ". " + boundary;
  }

  private boolean isConservative(RehabDtos.RehabCase rehabCase) {
    return rehabCase != null && ("elevated".equals(rehabCase.safety().level())
        || "uncertain".equals(rehabCase.safety().level()));
  }

  private String normalizeGender(String input) {
    if (input == null) return "male";
    String s = input.trim().toLowerCase();
    return "female".equals(s) ? "female" : "male";
  }

  private String normalizeGoal(String input) {
    if (input == null) return "maintenance";
    String s = input.trim().toLowerCase();
    return switch (s) {
      case "fat_loss", "muscle_gain", "rehab", "flexibility", "maintenance" -> s;
      case "body_shaping" -> "maintenance";
      case "auto" -> "rehab";
      default -> "maintenance";
    };
  }

  private String normalizeActivity(String input) {
    if (input == null) return "sedentary";
    String s = input.trim().toLowerCase();
    return switch (s) {
      case "light", "moderate", "active" -> s;
      default -> "sedentary";
    };
  }

  private double round(double value) {
    return Math.round(value * 10.0) / 10.0;
  }

  private long longValue(Object value) {
    return value instanceof Number n ? n.longValue() : 0L;
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private Integer nullableInt(Object value) {
    if (value == null) return null;
    if (value instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private int intValue(Object value, int fallback) {
    Integer parsed = nullableInt(value);
    return parsed == null ? fallback : parsed;
  }
}
