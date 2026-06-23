package com.ahealth.backend.rehab;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ahealth.backend.auth.AuthRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RehabController.class)
@AutoConfigureMockMvc(addFilters = false)
class RehabControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private RehabService rehabService;

  @MockitoBean
  private AuthRepository authRepository;

  @Test
  void planReturnsDashboardData() throws Exception {
    when(rehabService.getPlan()).thenReturn(planFixture());

    mockMvc.perform(get("/api/rehab/plan"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.label").value("今日计划"))
        .andExpect(jsonPath("$.exercises[0].name").value("Bird Dog"))
        .andExpect(jsonPath("$.planSummary.focus").value("Core stability"));
  }

  @Test
  void saveSettingsReturnsLatestSettings() throws Exception {
    RehabDtos.RehabPlanSettingsResponse payload = new RehabDtos.RehabPlanSettingsResponse(
        "Posture correction",
        "4 sessions per week",
        "25 minutes per session",
        "Moderate"
    );
    when(rehabService.savePlanSettings(payload)).thenReturn(payload);

    mockMvc.perform(post("/api/rehab/plan/settings")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.focus").value("Posture correction"))
        .andExpect(jsonPath("$.intensity").value("Moderate"));
  }

  private RehabDtos.RehabPlanResponse planFixture() {
    return new RehabDtos.RehabPlanResponse(
        "今日计划",
        List.of(new RehabDtos.RehabExercise(
            1L,
            "Bird Dog",
            "Core stability",
            "3 x 12",
            "basic",
            8,
            List.of("Keep a neutral spine", "Reach opposite arm and leg"),
            "Stop if you feel sharp low-back pain.",
            "Core stability",
            List.of("Improve trunk stability"),
            6,
            false
        )),
        new RehabDtos.RehabWeekTrend(
            List.of("03-12"),
            List.of(20),
            "Weekly training remains stable.",
            8
        ),
        new RehabDtos.RehabPlanSummary(
            "Core stability",
            "3 sessions per week",
            "20 minutes per session",
            "Low to moderate"
        ),
        new RehabDtos.RehabReminderSummary(
            "08:00",
            "Mon / Wed / Fri",
            "System notification",
            "Enabled"
        )
    );
  }
}
