package com.ahealth.backend.diet;

import com.ahealth.backend.common.JsonSupport;
import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.knowledge.InteractionCheckService;
import com.ahealth.backend.knowledge.KnowledgeDtos;
import com.ahealth.backend.knowledge.TcmFormulaService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 饮食推荐生成。
 *
 * <p>算法：Mifflin-St Jeor BMR + TDEE + 目标热量调整 + 宏量分配（蛋白 25%/30% / 脂肪 25% / 碳水 50%/45%）
 * + 三餐分配（早 30% / 午 40% / 晚 30%）+ 3 重食材过滤（过敏 + 西药 food_interaction + 中药 contraindication）。
 *
 * <p>不引入循环依赖：直接查 medications 表取用户药物清单（与 InteractionCheckService 模式一致）。
 */
@Service
public class DietService {
  private final JdbcTemplate jdbcTemplate;
  private final JsonSupport jsonSupport;
  private final FoodService foodService;
  private final DietPreferenceService dietPreferenceService;
  private final InteractionCheckService interactionCheckService;
  private final TcmFormulaService tcmFormulaService;

  public DietService(
      JdbcTemplate jdbcTemplate,
      JsonSupport jsonSupport,
      FoodService foodService,
      DietPreferenceService dietPreferenceService,
      InteractionCheckService interactionCheckService,
      TcmFormulaService tcmFormulaService
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonSupport = jsonSupport;
    this.foodService = foodService;
    this.dietPreferenceService = dietPreferenceService;
    this.interactionCheckService = interactionCheckService;
    this.tcmFormulaService = tcmFormulaService;
  }

  public DietDtos.DietPlanResponse generatePlan(long userId, DietDtos.DietPlanRequest req) {
    if (req == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "饮食计划参数不能为空");
    }
    // 1. 计算指标
    double heightCm = Math.max(0, req.height());
    double weightKg = Math.max(0, req.weight());
    int age = Math.max(1, req.age());
    String gender = normalizeGender(req.gender());
    String goal = normalizeGoal(req.goal());
    String activity = normalizeActivity(req.activityLevel());
    int mealCount = req.dailyMealCount() == null || req.dailyMealCount() < 1 ? 3 : Math.min(req.dailyMealCount(), 6);

    boolean hasBaseline = heightCm > 0 && weightKg > 0 && req.age() > 0;
    double bmi = hasBaseline ? weightKg / Math.pow(heightCm / 100.0, 2) : 0;
    double bmr = hasBaseline ? computeBmr(gender, weightKg, heightCm, age) : 0;
    double tdee = bmr > 0 ? bmr * activityFactor(activity) : 0;
    double targetCalories = tdee > 0 ? targetCalories(goal, tdee) : 0;

    // 宏量分配（基于目标）
    double proteinRatio = "muscle_gain".equals(goal) ? 0.30 : 0.25;
    double fatRatio = 0.25;
    double carbRatio = 1.0 - proteinRatio - fatRatio;
    double targetProteinG = targetCalories * proteinRatio / 4.0;
    double targetFatG = targetCalories * fatRatio / 9.0;
    double targetCarbG = targetCalories * carbRatio / 4.0;

    // 2. 综合忌口（过敏+西药 food_interaction+中药 contraindication）
    List<String> taboos = collectTaboos(userId);
    DietDtos.DietPreference preference = dietPreferenceService.getPreference(userId);
    List<String> disliked = preference.dislikedFoods();

    // 3. 三餐分配
    double[] mealRatios = computeMealRatios(mealCount);
    String[] mealNames = computeMealNames(mealCount);

    List<String> warnings = new ArrayList<>();
    if (!hasBaseline) {
      warnings.add("缺少年龄、身高或体重，暂不生成热量和餐次建议；请先完善个人资料。" );
      return new DietDtos.DietPlanResponse(
          0, "unknown", 0, 0, 0, 0, 0, 0, List.of(), taboos, warnings
      );
    }

    List<DietDtos.MealPlan> meals = new ArrayList<>();
    for (int i = 0; i < mealCount; i++) {
      double mealCalories = targetCalories * mealRatios[i];
      meals.add(buildMeal(mealNames[i], mealCalories, taboos, preference));
    }

    if (!taboos.isEmpty()) warnings.add("基于您的用药情况，已屏蔽 " + taboos.size() + " 类忌口食材");
    if (bmi >= 28) warnings.add("BMI 偏高，建议结合有氧运动");
    if (bmi > 0 && bmi < 18.5) warnings.add("BMI 偏低，建议增加蛋白质摄入");

    return new DietDtos.DietPlanResponse(
        round(bmi), bmiCategory(bmi),
        Math.round(bmr), Math.round(tdee), Math.round(targetCalories),
        Math.round(targetProteinG), Math.round(targetFatG), Math.round(targetCarbG),
        meals, taboos, warnings
    );
  }

  /** 供 Controller 单独查询忌口（综合过敏 + 西药 food_interaction + 中药 contraindication）。 */
  public List<String> collectTaboos(long userId) {
    Set<String> taboos = new HashSet<>();
    KnowledgeDtos.InteractionReport report = interactionCheckService.checkAllForUser(userId);
    report.drugFoodInteractions().forEach(r -> {
      if (r.drugB() != null && !r.drugB().isBlank()) taboos.add(r.drugB());
      if (r.description() != null && r.description().contains("忌口")) taboos.add(r.description());
    });

    // 中药方剂的药材 contraindication
    List<String> herbNames = collectUserHerbNames(userId);
    for (String herb : herbNames) {
      try {
        var row = jdbcTemplate.queryForMap(
            "SELECT contraindication FROM tcm_herbs WHERE name = ? LIMIT 1", herb);
        String c = String.valueOf(row.get("contraindication"));
        if (!c.isBlank() && !c.equals("null")) taboos.add(herb + "忌口：" + c);
      } catch (Exception ignored) {
        // 药材不在表中，忽略
      }
    }
    return new ArrayList<>(taboos);
  }

  private List<String> collectUserHerbNames(long userId) {
    List<String> names = new ArrayList<>();
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT medicine_type, formula_id, name FROM medications WHERE user_id = ? AND enabled = 1", userId);
    for (var row : rows) {
      String type = String.valueOf(row.get("medicine_type"));
      Long formulaId = toLong(row.get("formula_id"));
      if ("formula".equalsIgnoreCase(type) && formulaId != null) {
        names.addAll(tcmFormulaService.getFormulaHerbNames(formulaId));
      } else if ("tcm".equalsIgnoreCase(type)) {
        names.add(String.valueOf(row.get("name")));
      }
    }
    return names;
  }

  private DietDtos.MealPlan buildMeal(
      String mealType,
      double targetCalories,
      List<String> taboos,
      DietDtos.DietPreference preference
  ) {
    // 按 category 分配：谷物 30% / 蛋白(肉类+乳+豆) 30% / 蔬菜水果 30%
    List<DietDtos.MealItem> items = new ArrayList<>();
    double grainCal = targetCalories * 0.30;
    double proteinCal = targetCalories * 0.30;
    double vegCal = targetCalories * 0.30;

    items.add(pickFoodByCategory("谷物", grainCal, taboos, preference));
    items.add(pickFoodByCategoryProtein(proteinCal, taboos, preference));
    items.add(pickFoodByCategory("蔬菜", vegCal, taboos, preference));

    return new DietDtos.MealPlan(mealType, targetCalories, items);
  }

  private DietDtos.MealItem pickFoodByCategory(
      String category,
      double targetCalories,
      List<String> taboos,
      DietDtos.DietPreference preference
  ) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        """
        SELECT id, name, calories_per_100g, protein_g, fat_g, carb_g
        FROM food_items
        WHERE category = ?
        ORDER BY id LIMIT 5
        """, category);
    for (var row : rows) {
      String name = String.valueOf(row.get("name"));
      if (isSafe(name, category, taboos, preference)) {
        double calPer100g = toDouble(row.get("calories_per_100g"));
        double quantityG = calPer100g > 0 ? targetCalories / calPer100g * 100 : 100;
        quantityG = Math.min(quantityG, 300);
        return buildItem(row, quantityG);
      }
    }
    // fallback: 取第一条
    if (!rows.isEmpty()) {
      for (var row : rows) {
        if (isSafe(String.valueOf(row.get("name")), category, taboos, preference)) {
          return buildItem(row, 100);
        }
      }
    }
    return new DietDtos.MealItem(0, "（无匹配）", category, 100, 0, 0, 0, 0);
  }

  private DietDtos.MealItem pickFoodByCategoryProtein(
      double targetCalories,
      List<String> taboos,
      DietDtos.DietPreference preference
  ) {
    // 优先 鸡胸肉/瘦猪肉/牛肉/鱼肉/鸡蛋/豆腐 轮换
    String[] candidates = preference.vegetarian()
        ? new String[]{"豆腐", "鸡蛋"}
        : new String[]{"鸡胸肉", "瘦猪肉", "牛肉", "鱼肉", "鸡蛋", "豆腐"};
    for (String name : candidates) {
      if (isSafe(name, preference.vegetarian() ? "豆制品" : "肉类", taboos, preference)) {
        try {
          var row = jdbcTemplate.queryForMap(
              "SELECT id, name, calories_per_100g, protein_g, fat_g, carb_g FROM food_items WHERE name = ? LIMIT 1",
              name);
          double calPer100g = toDouble(row.get("calories_per_100g"));
          double quantityG = calPer100g > 0 ? targetCalories / calPer100g * 100 : 100;
          quantityG = Math.min(quantityG, 250);
          return buildItem(row, quantityG);
        } catch (Exception ignored) {
          // 食材不在表中，继续尝试下一个
        }
      }
    }
    return pickFoodByCategory(preference.vegetarian() ? "豆制品" : "肉类", targetCalories, taboos, preference);
  }

  private boolean isSafe(
      String foodName,
      String category,
      List<String> taboos,
      DietDtos.DietPreference preference
  ) {
    if (foodName == null) return false;
    if (preference.vegetarian() && isAnimalCategory(category, foodName)) return false;
    String lower = foodName.toLowerCase();
    for (String t : taboos) {
      if (t == null) continue;
      if (t.contains(foodName) || lower.contains(t.toLowerCase())) return false;
    }
    for (String d : preference.dislikedFoods()) {
      if (d == null) continue;
      if (foodName.contains(d) || d.contains(foodName)) return false;
    }
    if (preference.avoidSpicy() && containsAny(lower, "辣", "椒", "麻辣", "辛辣")) return false;
    if (preference.avoidCold() && containsAny(lower, "冰", "冷饮", "生冷", "凉")) return false;
    return true;
  }

  private boolean isAnimalCategory(String category, String foodName) {
    String value = (category + " " + foodName).toLowerCase();
    return containsAny(value, "肉", "鱼", "虾", "蟹", "奶", "乳", "蛋", "海鲜");
  }

  private boolean containsAny(String value, String... terms) {
    for (String term : terms) {
      if (value.contains(term)) return true;
    }
    return false;
  }

  private DietDtos.MealItem buildItem(Map<String, Object> row, double quantityG) {
    double calPer100g = toDouble(row.get("calories_per_100g"));
    double factor = quantityG / 100.0;
    return new DietDtos.MealItem(
        toLong(row.get("id")),
        String.valueOf(row.get("name")),
        String.valueOf(row.getOrDefault("category", "")),
        Math.round(quantityG * 10) / 10.0,
        Math.round(calPer100g * factor * 10) / 10.0,
        Math.round(toDouble(row.get("protein_g")) * factor * 10) / 10.0,
        Math.round(toDouble(row.get("fat_g")) * factor * 10) / 10.0,
        Math.round(toDouble(row.get("carb_g")) * factor * 10) / 10.0
    );
  }

  // ===== 公式（与 SmartRehabPlannerService 一致）=====

  private double computeBmr(String gender, double weightKg, double heightCm, int age) {
    double base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age;
    return "female".equals(gender) ? base - 161 : base + 5;
  }

  private double activityFactor(String level) {
    return switch (level) {
      case "light" -> 1.375;
      case "moderate" -> 1.55;
      case "active" -> 1.725;
      default -> 1.2;
    };
  }

  private double targetCalories(String goal, double tdee) {
    return switch (goal) {
      case "fat_loss" -> tdee - 500;
      case "muscle_gain" -> tdee + 300;
      default -> tdee;
    };
  }

  private String bmiCategory(double bmi) {
    if (bmi <= 0) return "unknown";
    if (bmi < 18.5) return "underweight";
    if (bmi < 24) return "normal";
    if (bmi < 28) return "overweight";
    return "obese";
  }

  private double[] computeMealRatios(int mealCount) {
    return switch (mealCount) {
      case 2 -> new double[]{0.4, 0.6};
      case 4 -> new double[]{0.25, 0.35, 0.25, 0.15};
      case 5 -> new double[]{0.2, 0.3, 0.25, 0.15, 0.1};
      case 6 -> new double[]{0.2, 0.25, 0.25, 0.15, 0.1, 0.05};
      default -> new double[]{0.3, 0.4, 0.3}; // 3 餐
    };
  }

  private String[] computeMealNames(int mealCount) {
    return switch (mealCount) {
      case 2 -> new String[]{"lunch", "dinner"};
      case 4 -> new String[]{"breakfast", "lunch", "afternoon", "dinner"};
      case 5 -> new String[]{"breakfast", "morning_snack", "lunch", "afternoon", "dinner"};
      case 6 -> new String[]{"breakfast", "morning_snack", "lunch", "afternoon", "dinner", "evening_snack"};
      default -> new String[]{"breakfast", "lunch", "dinner"};
    };
  }

  private String normalizeGender(String s) { return "female".equalsIgnoreCase(s) ? "female" : "male"; }

  private String normalizeGoal(String s) {
    if (s == null) return "maintenance";
    return switch (s.trim().toLowerCase()) {
      case "fat_loss", "muscle_gain", "maintenance" -> s.trim().toLowerCase();
      default -> "maintenance";
    };
  }

  private String normalizeActivity(String s) {
    if (s == null) return "sedentary";
    return switch (s.trim().toLowerCase()) {
      case "light", "moderate", "active" -> s.trim().toLowerCase();
      default -> "sedentary";
    };
  }

  private double round(double v) { return Math.round(v * 10.0) / 10.0; }

  private double toDouble(Object v) { return v instanceof Number n ? n.doubleValue() : 0.0; }

  private long toLong(Object v) {
    if (v == null) return 0L;
    if (v instanceof Number n) {
      return n.longValue();
    }
    try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return 0L; }
  }
}
