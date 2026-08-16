package com.ahealth.backend.diet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class DietDtos {
  private DietDtos() {}

  public record FoodSearchItem(
      long id,
      String name,
      String category,
      double caloriesPer100g,
      double proteinG,
      double fatG,
      double carbG,
      double fiberG,
      double sodiumMg,
      double potassiumMg,
      Integer glycemicIndex,
      List<String> tags
  ) {}

  public record FoodNutrition(
      double calories,
      double protein,
      double carbs,
      double fat,
      double fiber,
      double sodium,
      double potassium
  ) {}

  /** 多模态识别结果；营养值只在食物库命中时填充。 */
  public record FoodRecognitionResponse(
      String foodName,
      String category,
      double confidence,
      double weightGrams,
      String portion,
      FoodNutrition per100g,
      double calories,
      double protein,
      double carbs,
      double fat,
      String source,
      List<String> warnings
  ) {}

  public record DietLogSaveRequest(
      String foodName,
      String category,
      double weightGrams,
      double calories,
      double protein,
      double carbs,
      double fat,
      String source,
      LocalDateTime recordedAt
  ) {}

  public record DietLogEntry(
      long id,
      String foodName,
      String category,
      double weightGrams,
      double calories,
      double protein,
      double carbs,
      double fat,
      String source,
      LocalDateTime recordedAt
  ) {}

  public record DietLogAuditEntry(
      long id,
      long dietLogId,
      String action,
      String beforeJson,
      String afterJson,
      String reason,
      LocalDateTime createdAt
  ) {}

  public record DietLogOperationResult(
      boolean success,
      long dietLogId,
      String message
  ) {}

  public record DietPlanRequest(
      double height,
      double weight,
      int age,
      String gender,
      String goal,             // fat_loss / muscle_gain / maintenance
      String activityLevel,    // sedentary / light / moderate / active
      Integer dailyMealCount   // 默认 3
  ) {}

  public record MealItem(
      long foodId,
      String foodName,
      String category,
      double quantityG,
      double calories,
      double proteinG,
      double fatG,
      double carbG
  ) {}

  public record MealPlan(
      String mealType,         // breakfast / lunch / dinner / ...
      double targetCalories,
      List<MealItem> items
  ) {}

  public record DietPlanResponse(
      double bmi,
      String bmiCategory,
      double bmr,
      double tdee,
      double targetCalories,
      double targetProteinG,
      double targetFatG,
      double targetCarbG,
      List<MealPlan> meals,
      List<String> taboos,           // 综合忌口（过敏+西药 food_interaction+中药 contraindication）
      List<String> warnings
  ) {}

  public record DietPreferenceSaveRequest(
      String dietStyle,           // balanced / low_carb / high_protein / vegetarian
      List<String> dislikedFoods,
      String preferredCuisine,
      Integer dailyMealCount,
      Boolean avoidSpicy,
      Boolean avoidCold,
      Boolean vegetarian
  ) {}

  public record DietPreference(
      String dietStyle,
      List<String> dislikedFoods,
      String preferredCuisine,
      int dailyMealCount,
      boolean avoidSpicy,
      boolean avoidCold,
      boolean vegetarian,
      LocalDate updatedAt
  ) {}
}
