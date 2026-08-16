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
  private SmartRehabPlannerService smartRehabPlannerService;

  @MockitoBean
  private RehabCaseService rehabCaseService;

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

  @Test
  void currentCaseReturnsEvidenceAndSafetyBoundary() throws Exception {
    when(rehabCaseService.buildCurrentCase()).thenReturn(caseFixture());

    mockMvc.perform(get("/api/rehab/case"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caseId").value("case-1"))
        .andExpect(jsonPath("$.evidence[0].sourceType").value("monitoring_baseline"))
        .andExpect(jsonPath("$.safety.level").value("elevated"))
        .andExpect(jsonPath("$.safety.flags[0]").value("PROFILE_HIGH_RISK"));
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

  private RehabDtos.RehabCase caseFixture() {
    return new RehabDtos.RehabCase(
        "case-1",
        "2026-08-02T00:00:00Z",
        "rehab-case-v1",
        new RehabDtos.CaseProfile(170, 70, 70, "unknown", "rehab", "sedentary", "profile"),
        new RehabDtos.CaseMonitoring(96, 60, 70, 20, 1800, 80, "high"),
        new RehabDtos.CaseMedication(1, List.of("beta blocker"), List.of("review")),
        List.of(),
        List.of(new RehabDtos.CaseEvidence("monitoring-30d", "monitoring_baseline", "baseline", "rolling_30_days")),
        List.of(new RehabDtos.PlanConstraint("PROFILE_HIGH_RISK", "high", "risk", "review")),
        new RehabDtos.PlanSafety("elevated", List.of("PROFILE_HIGH_RISK"), "uncertain", "clinician_review"));
  }
}
