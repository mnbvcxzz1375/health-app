package com.ahealth.backend.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PatientMemoryPolicyTest {

  @Test
  void chronicConditionIsConfirmedLongTermSafetyMemory() {
    PatientMemoryPolicy.Rule rule = PatientMemoryPolicy.ruleFor("chronic_condition");

    assertEquals(PatientMemoryPolicy.Tier.LONG_TERM, rule.tier());
    assertNull(rule.retention());
    assertTrue(rule.requiresConfirmation());
    assertTrue(rule.safetyCritical());
  }

  @Test
  void rehabilitationPhaseHasBoundedCareCycleRetention() {
    PatientMemoryPolicy.Rule rule = PatientMemoryPolicy.ruleFor("rehab_phase");

    assertEquals(PatientMemoryPolicy.Tier.CARE_CYCLE, rule.tier());
    assertEquals(90, rule.retention().toDays());
    assertFalse(rule.requiresConfirmation());
  }

  @Test
  void consultSummaryExpiresBeforeItCanPolluteLongTermMemory() {
    PatientMemoryPolicy.Rule rule = PatientMemoryPolicy.ruleFor("consult_summary");

    assertEquals(PatientMemoryPolicy.Tier.ENCOUNTER, rule.tier());
    assertEquals(7, rule.retention().toDays());
    assertFalse(rule.safetyCritical());
  }

  @Test
  void unknownMemoryTypesAreRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> PatientMemoryPolicy.ruleFor("model_inferred_diagnosis"));
  }
}
