package com.ahealth.backend.diet;

import com.ahealth.backend.ai.DashScopeService;
import com.ahealth.backend.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 食物图片识别：多模态模型负责视觉候选，food_items 负责营养事实。
 *
 * <p>模型不能直接编造营养值；只有在本地食物库命中后才计算热量，否则返回明确的
 * "未匹配" 警告，供前端阻止误导性展示。
 */
@Service
public class FoodRecognitionService {
  private static final String SYSTEM_PROMPT = """
      你是健康饮食图像预识别模块，不做医学诊断。
      仅根据图片给出最可能的一个食物候选和粗略分量，无法确定时降低置信度。
      只返回 JSON，不要 Markdown，结构必须为：
      {"food_name":"","category":"","confidence":0.0,"weight_grams":0.0,"portion":"","search_keyword":"","warnings":[]}
      confidence 使用 0 到 1 的小数；weight_grams 只能是视觉估计，不能声称精确称量。
      """;

  private final DashScopeService dashScopeService;
  private final FoodService foodService;

  public FoodRecognitionService(
      DashScopeService dashScopeService,
      FoodService foodService
  ) {
    this.dashScopeService = dashScopeService;
    this.foodService = foodService;
  }

  public DietDtos.FoodRecognitionResponse recognize(MultipartFile file) {
    validate(file);
    List<Object> content = new ArrayList<>();
    content.add(Map.of(
        "type", "text",
        "text", "识别图片中的主要食物，给出食物库搜索关键词和非精确的分量估计。"
    ));
    content.addAll(dashScopeService.toImageBlocks(new MultipartFile[]{file}));

    JsonNode model = dashScopeService.requestJson(
        SYSTEM_PROMPT,
        content,
        dashScopeService.visionModel(),
        0.15,
        "食物图片识别"
    );

    String modelName = text(model, "food_name");
    String keyword = text(model, "search_keyword");
    if (keyword.isBlank()) keyword = modelName;
    double confidence = normalizeConfidence(number(model, "confidence"));
    double weight = clamp(number(model, "weight_grams"), 1, 2000);
    String portion = text(model, "portion");
    List<String> warnings = stringList(model.path("warnings"));

    DietDtos.FoodSearchItem match = findMatch(keyword, modelName);
    if (match == null) {
      addWarning(warnings, "未在营养食物库中命中可靠条目，未计算热量；请手动核对食物和分量。" );
      return new DietDtos.FoodRecognitionResponse(
          modelName.isBlank() ? "未识别" : modelName,
          text(model, "category"),
          confidence,
          weight,
          portion,
          new DietDtos.FoodNutrition(0, 0, 0, 0, 0, 0, 0),
          0, 0, 0, 0,
          "vision_unmatched",
          List.copyOf(warnings)
      );
    }

    double factor = weight / 100.0;
    DietDtos.FoodNutrition nutrition = new DietDtos.FoodNutrition(
        match.caloriesPer100g(), match.proteinG(), match.carbG(), match.fatG(),
        match.fiberG(), match.sodiumMg(), match.potassiumMg()
    );
    return new DietDtos.FoodRecognitionResponse(
        match.name(), match.category(), confidence, weight, portion, nutrition,
        round(match.caloriesPer100g() * factor),
        round(match.proteinG() * factor),
        round(match.carbG() * factor),
        round(match.fatG() * factor),
        "vision_food_catalog",
        List.copyOf(warnings)
    );
  }

  private DietDtos.FoodSearchItem findMatch(String keyword, String modelName) {
    try {
      List<DietDtos.FoodSearchItem> candidates = foodService.searchFoods(keyword, 10);
      if (candidates.isEmpty() && !modelName.isBlank() && !modelName.equals(keyword)) {
        candidates = foodService.searchFoods(modelName, 10);
      }
      if (candidates.isEmpty()) return null;
      String normalized = keyword.trim();
      for (DietDtos.FoodSearchItem item : candidates) {
        if (item.name().equalsIgnoreCase(normalized)) return item;
      }
      return candidates.get(0);
    } catch (Exception ignored) {
      return null;
    }
  }

  private void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "请上传食物图片。");
    }
    String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
    if (!type.startsWith("image/")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "食物识别仅支持图片文件。");
    }
    if (file.getSize() > 10 * 1024 * 1024) {
      throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "图片不能超过 10MB。");
    }
  }

  private String text(JsonNode node, String key) {
    return node == null ? "" : node.path(key).asText("").trim();
  }

  private double number(JsonNode node, String key) {
    JsonNode value = node == null ? null : node.get(key);
    return value != null && value.isNumber() ? value.asDouble() : 0;
  }

  private double normalizeConfidence(double value) {
    if (value > 1 && value <= 100) return Math.min(100, value);
    return Math.round(Math.max(0, Math.min(1, value)) * 1000) / 10.0;
  }

  private double clamp(double value, double min, double max) {
    return value <= 0 ? 0 : Math.min(max, Math.max(min, value));
  }

  private double round(double value) {
    return Math.round(value * 10) / 10.0;
  }

  private List<String> stringList(JsonNode node) {
    if (node == null || !node.isArray()) return new ArrayList<>();
    List<String> values = new ArrayList<>();
    node.forEach(item -> {
      String value = item.asText("").trim();
      if (!value.isBlank()) values.add(value);
    });
    return values;
  }

  private void addWarning(List<String> warnings, String warning) {
    if (!warnings.contains(warning)) warnings.add(warning);
  }
}
