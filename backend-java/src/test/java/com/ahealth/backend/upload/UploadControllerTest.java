package com.ahealth.backend.upload;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UploadController.class)
@AutoConfigureMockMvc(addFilters = false)
class UploadControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private UploadService uploadService;

  @MockitoBean
  private AuthRepository authRepository;

  @Test
  void customModelRouteReturnsStructuredFallbackReport() throws Exception {
    UploadDtos.AnalyzeReport report = new UploadDtos.AnalyzeReport(
        "lab report", "summary", "medium", List.of("point"), List.of("advice"),
        "rehab", List.of("follow up"), "caution");
    when(uploadService.createTaskByCustomModel(anyString(), anyString(), isNull()))
        .thenReturn(new UploadDtos.CustomModelTaskResponse(
            "task-1", "lab", "llm_fallback", null, report));

    mockMvc.perform(multipart("/api/analyze/tasks/custom-model")
            .param("type", "lab")
            .param("text", "recent lab report"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taskId").value("task-1"))
        .andExpect(jsonPath("$.type").value("lab"))
        .andExpect(jsonPath("$.source").value("llm_fallback"))
        .andExpect(jsonPath("$.analyzeReport.title").value("lab report"));
  }
}
