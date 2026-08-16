package com.ahealth.backend.diet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ahealth.backend.ai.DashScopeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FoodRecognitionServiceTest {

  @Test
  void matchesCatalogBeforeCalculatingNutrition() throws Exception {
    DashScopeService dashScope = mock(DashScopeService.class);
    FoodService foodService = mock(FoodService.class);
    when(dashScope.visionModel()).thenReturn("test-vision");
    when(dashScope.toImageBlocks(any())).thenReturn(List.of(Map.of("type", "image_url")));
    when(dashScope.requestJson(anyString(), any(), anyString(), anyDouble(), anyString()))
        .thenReturn(new ObjectMapper().readTree("""
            {"food_name":"燕麦","category":"谷物","confidence":0.8,
             "weight_grams":50,"portion":"小碗","search_keyword":"燕麦","warnings":[]}
            """));
    when(foodService.searchFoods("燕麦", 10)).thenReturn(List.of(new DietDtos.FoodSearchItem(
        1, "燕麦", "谷物", 389, 17, 7, 67, 10, 2, 429, 55, List.of("高纤")
    )));

    FoodRecognitionService service = new FoodRecognitionService(dashScope, foodService);
    DietDtos.FoodRecognitionResponse response = service.recognize(
        new MockMultipartFile("file", "food.jpg", "image/jpeg", new byte[]{1, 2, 3})
    );

    assertEquals("燕麦", response.foodName());
    assertEquals("vision_food_catalog", response.source());
    assertEquals(194.5, response.calories());
    assertEquals(80, response.confidence());
    assertTrue(response.warnings().isEmpty());
  }

  @Test
  void unmatchedCandidateDoesNotInventNutrition() throws Exception {
    DashScopeService dashScope = mock(DashScopeService.class);
    FoodService foodService = mock(FoodService.class);
    when(dashScope.visionModel()).thenReturn("test-vision");
    when(dashScope.toImageBlocks(any())).thenReturn(List.of(Map.of("type", "image_url")));
    when(dashScope.requestJson(anyString(), any(), anyString(), anyDouble(), anyString()))
        .thenReturn(new ObjectMapper().readTree("""
            {"food_name":"未知菜品","confidence":0.6,"weight_grams":300,"warnings":[]}
            """));
    when(foodService.searchFoods(anyString(), anyInt())).thenReturn(List.of());

    FoodRecognitionService service = new FoodRecognitionService(dashScope, foodService);
    DietDtos.FoodRecognitionResponse response = service.recognize(
        new MockMultipartFile("file", "food.jpg", "image/jpeg", new byte[]{1})
    );

    assertEquals("vision_unmatched", response.source());
    assertEquals(0, response.calories());
    assertTrue(response.warnings().stream().anyMatch(value -> value.contains("未在营养食物库")));
  }
}
