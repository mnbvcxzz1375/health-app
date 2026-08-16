package com.ahealth.backend.rehab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ahealth.backend.context.ContextDtos;
import com.ahealth.backend.context.ContextService;
import com.ahealth.backend.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RehabCaseServiceTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void highRiskCaseProducesBoundedSafetyConstraints() {
    ContextService contextService = mock(ContextService.class);
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(contextService.getSnapshot()).thenReturn(new ContextDtos.ContextSnapshot(
        "system", "daily", List.of("high risk"), List.of("beta blocker"), List.of(),
        new ContextDtos.UserHealthBaseline(98, 55, 78, 25, 1800, 82, "high"),
        new ContextDtos.MedicationContextSummary(1, List.of("beta blocker"), List.of("review")),
        new ContextDtos.InteractionMemorySummary(2, List.of("rehab"), "2026-08-02T00:00:00Z")));
    doReturn(List.of()).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));
    setUser(7L);

    RehabCaseService service = new RehabCaseService(contextService, jdbcTemplate, new ObjectMapper());
    RehabDtos.RehabCase rehabCase = service.buildCurrentCase(new RehabDtos.SmartPlanRequest(
        170, 70, 30, "unknown", "body_shaping", "active", "manual"));

    assertEquals("elevated", rehabCase.safety().level());
    assertTrue(rehabCase.constraints().stream()
        .anyMatch(item -> "PROFILE_HIGH_RISK".equals(item.code())));
    assertTrue(rehabCase.constraints().stream()
        .anyMatch(item -> "HIGH_RESTING_HEART_RATE".equals(item.code())));
    assertTrue(rehabCase.constraints().stream()
        .anyMatch(item -> "ACTIVE_MEDICATION_REVIEW".equals(item.code())));
    assertTrue(rehabCase.safety().actionTags().contains("HOLD_PROGRESSION"));
    assertTrue(rehabCase.safety().actionTags().contains("PHARMACIST_OR_CLINICIAN_REVIEW"));
    assertEquals("monitoring_baseline", rehabCase.evidence().get(0).sourceType());
    assertEquals("rolling_30_days", rehabCase.timeRange().label());
    assertEquals("not_available", rehabCase.posture().status());
  }

  @Test
  void missingMonitoringAndMedicationEvidenceIsExplicit() {
    ContextService contextService = mock(ContextService.class);
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(contextService.getSnapshot()).thenReturn(new ContextDtos.ContextSnapshot(
        "", "暂无监测数据", List.of(), List.of(), List.of(),
        new ContextDtos.UserHealthBaseline(72, 76, 50, 0, 0, 18, "低风险"),
        new ContextDtos.MedicationContextSummary(0, List.of(), List.of()),
        new ContextDtos.InteractionMemorySummary(0, List.of(), "")));
    doReturn(List.of()).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));
    setUser(8L);

    RehabCaseService service = new RehabCaseService(contextService, jdbcTemplate, new ObjectMapper());
    RehabDtos.RehabCase rehabCase = service.buildCurrentCase(
        new RehabDtos.SmartPlanRequest(0, 0, 0, "unknown", "rehab", "sedentary", "manual"));

    assertTrue(rehabCase.safety().flags().contains("INCOMPLETE_PROFILE"));
    assertTrue(rehabCase.safety().flags().contains("NO_CASE_EVIDENCE"));
    assertEquals("elevated", rehabCase.safety().level());
    assertTrue(rehabCase.safety().actionTags().contains("NO_PERSONALIZED_GUIDANCE"));
    assertTrue(rehabCase.safety().actionTags().contains("REQUEST_MORE_EVIDENCE"));
    assertTrue(rehabCase.evidence().isEmpty());
  }

  @Test
  void incompleteProfileRemainsConservativeEvenWhenMedicationEvidenceExists() {
    ContextService contextService = mock(ContextService.class);
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(contextService.getSnapshot()).thenReturn(new ContextDtos.ContextSnapshot(
        "", "暂无监测数据", List.of(), List.of("beta blocker"), List.of(),
        new ContextDtos.UserHealthBaseline(72, 0, 0, 0, 0, 18, "低风险"),
        new ContextDtos.MedicationContextSummary(1, List.of("beta blocker"), List.of("review")),
        new ContextDtos.InteractionMemorySummary(0, List.of(), "")));
    doReturn(List.of()).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));
    setUser(9L);

    RehabCaseService service = new RehabCaseService(contextService, jdbcTemplate, new ObjectMapper());
    RehabDtos.RehabCase rehabCase = service.buildCurrentCase(
        new RehabDtos.SmartPlanRequest(0, 0, 0, "unknown", "rehab", "sedentary", "manual"));

    assertEquals("elevated", rehabCase.safety().level());
    assertTrue(rehabCase.constraints().stream()
        .anyMatch(item -> "INCOMPLETE_PROFILE".equals(item.code())));
    assertTrue(rehabCase.safety().actionTags().contains("NO_PERSONALIZED_GUIDANCE"));
    assertTrue(rehabCase.safety().actionTags().contains("LOWER_INTENSITY"));
  }

  @Test
  void confirmedLongTermSafetyMemoryConstrainsRehabPlan() {
    ContextService contextService = mock(ContextService.class);
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    ContextDtos.PatientMemoryItem contraindication = new ContextDtos.PatientMemoryItem(
        41L, "long_term", "functional_limit", "Avoid unsupported knee flexion beyond tolerance.",
        "user_confirmed", "elevated", true, true, "2026-08-03T00:00:00Z", "");
    when(contextService.getSnapshot()).thenReturn(new ContextDtos.ContextSnapshot(
        "system", "daily", List.of(), List.of(), List.of(),
        new ContextDtos.UserHealthBaseline(72, 76, 50, 0, 0, 18, "low"),
        new ContextDtos.MedicationContextSummary(0, List.of(), List.of()),
        new ContextDtos.InteractionMemorySummary(0, List.of(), ""),
        new ContextDtos.PatientMemoryBrief(List.of(contraindication), List.of(), List.of(),
            List.of(contraindication))));
    doReturn(List.of()).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));
    setUser(10L);

    RehabCaseService service = new RehabCaseService(contextService, jdbcTemplate, new ObjectMapper());
    RehabDtos.RehabCase rehabCase = service.buildCurrentCase(
        new RehabDtos.SmartPlanRequest(170, 70, 70, "unknown", "rehab", "sedentary", "manual"));

    assertTrue(rehabCase.constraints().stream()
        .anyMatch(item -> "CONFIRMED_LONG_TERM_SAFETY_MEMORY".equals(item.code())));
    assertTrue(rehabCase.safety().actionTags().contains("RESPECT_CONFIRMED_SAFETY_MEMORY"));
    assertTrue(rehabCase.evidence().stream()
        .anyMatch(item -> "long_term_memory".equals(item.sourceType())));
  }

  private void setUser(long userId) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            new AuthenticatedUser(userId, "test-token", "Test User", "test@example.com"), null));
  }
}
