package com.ahealth.backend.rehab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import com.ahealth.backend.context.ContextDtos;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SmartRehabPlannerServiceTest {

  @Test
  void incompleteCaseDoesNotInventCalorieBaselineOrNormalIntensity() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    RehabCaseService caseService = mock(RehabCaseService.class);
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
    RehabDtos.SmartPlanRequest request = new RehabDtos.SmartPlanRequest(
        0, 0, 0, "unknown", "maintenance", "active", "manual");
    RehabDtos.RehabCase incompleteCase = new RehabDtos.RehabCase(
        "case-no-evidence", "2026-08-02T00:00:00Z", "rehab-case-v1",
        new RehabDtos.CaseProfile(0, 0, 0, "unknown", "maintenance", "active", "manual"),
        new RehabDtos.CaseMonitoring(0, 0, 0, 0, 0, 0, "unknown"),
        new RehabDtos.CaseMedication(0, List.of(), List.of()), List.of(), List.of(), List.of(),
        new RehabDtos.PlanSafety("uncertain", List.of("NO_CASE_EVIDENCE"), "missing", "uncertain",
            List.of("NO_PERSONALIZED_GUIDANCE", "REQUEST_MORE_EVIDENCE")));
    when(caseService.buildCurrentCase(request)).thenReturn(incompleteCase);

    SmartRehabPlannerService service = new SmartRehabPlannerService(jdbcTemplate, caseService);
    RehabDtos.SmartPlanResponse response = service.generatePlan(request);

    assertEquals(0, response.bmr());
    assertEquals(0, response.tdee());
    assertEquals(0, response.targetCalories());
    assertEquals(0, response.dietSuggestion().targetProtein());
    assertTrue(response.dietSuggestion().tips().get(0).contains("缺少完整"));
    assertTrue(response.summary().contains("generic low-intensity starting point"));
  }
}
