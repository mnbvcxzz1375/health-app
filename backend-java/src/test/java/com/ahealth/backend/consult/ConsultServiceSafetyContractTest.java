package com.ahealth.backend.consult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsultServiceSafetyContractTest {

  private final ConsultService service = new ConsultService(
      null, null, null, null, null, null, new ObjectMapper(), null, null, null, null, null);

  @Test
  void modelCannotDowngradeDeterministicSafety() {
    ConsultDtos.SafetyInfo deterministic = new ConsultDtos.SafetyInfo(
        "elevated", List.of("HIGH_RESTING_HEART_RATE"), "deterministic", "clinician_review",
        List.of("LOWER_INTENSITY"));
    ConsultDtos.SafetyInfo model = new ConsultDtos.SafetyInfo(
        "routine", List.of(), "model", "routine_review", List.of("REASSESS_BEFORE_PROGRESSION"));

    ConsultDtos.SafetyInfo merged = service.mergeSafety(deterministic, model);

    assertEquals("elevated", merged.level());
    assertEquals("clinician_review", merged.escalation());
    assertTrue(merged.flags().contains("HIGH_RESTING_HEART_RATE"));
    assertTrue(merged.actionTags().contains("LOWER_INTENSITY"));
    assertTrue(merged.actionTags().contains("REASSESS_BEFORE_PROGRESSION"));
  }

  @Test
  void modelEmergencyMetadataGetsMandatoryActions() {
    ConsultDtos.SafetyInfo routine = new ConsultDtos.SafetyInfo(
        "routine", List.of(), "", "routine_review", List.of());
    ConsultDtos.SafetyInfo model = new ConsultDtos.SafetyInfo(
        "emergency", List.of("EMERGENCY_ESCALATION"), "uncertain", "emergency", List.of());

    ConsultDtos.SafetyInfo merged = service.mergeSafety(routine, model);

    assertEquals("emergency", merged.level());
    assertEquals("emergency", merged.escalation());
    assertTrue(merged.actionTags().containsAll(
        List.of("STOP_ACTIVITY", "EMERGENCY_CARE", "NO_DOSAGE_INFERENCE")));
  }

  @Test
  void missingEvidenceBecomesExplicitUncertainContract() {
    ConsultDtos.SafetyInfo routine = new ConsultDtos.SafetyInfo(
        "routine", List.of(), "", "routine_review", List.of("REASSESS_BEFORE_PROGRESSION"));

    ConsultDtos.SafetyInfo parsed = service.parseSafety(null, routine, List.of());

    assertEquals("uncertain", parsed.level());
    assertEquals("uncertain", parsed.escalation());
    assertTrue(parsed.flags().contains("NO_RETRIEVED_EVIDENCE"));
    assertTrue(parsed.actionTags().containsAll(
        List.of("NO_PERSONALIZED_GUIDANCE", "REQUEST_MORE_EVIDENCE")));
  }
}
