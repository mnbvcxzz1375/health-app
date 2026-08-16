package com.ahealth.backend.diet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ahealth.backend.auth.AuthRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DietController.class)
@AutoConfigureMockMvc(addFilters = false)
class DietControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private FoodService foodService;

  @MockitoBean
  private DietService dietService;

  @MockitoBean
  private DietPreferenceService dietPreferenceService;

  @MockitoBean
  private FoodRecognitionService foodRecognitionService;

  @MockitoBean
  private DietLogService dietLogService;

  @MockitoBean
  private AuthRepository authRepository;

  @Test
  void recognizeEndpointReturnsCatalogBackedContract() throws Exception {
    when(foodRecognitionService.recognize(any())).thenReturn(new DietDtos.FoodRecognitionResponse(
        "燕麦", "谷物", 86, 50, "小碗",
        new DietDtos.FoodNutrition(389, 17, 67, 7, 10, 2, 429),
        194.5, 8.5, 33.5, 3.5, "vision_food_catalog", List.of()
    ));

    MockMultipartFile file = new MockMultipartFile(
        "file", "food.jpg", "image/jpeg", new byte[]{1, 2, 3}
    );
    mockMvc.perform(multipart("/api/diet/recognize").file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.foodName").value("燕麦"))
        .andExpect(jsonPath("$.source").value("vision_food_catalog"))
        .andExpect(jsonPath("$.per100g.sodium").value(2));
  }
}
