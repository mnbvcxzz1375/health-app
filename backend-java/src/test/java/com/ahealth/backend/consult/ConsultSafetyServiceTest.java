package com.ahealth.backend.consult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConsultSafetyServiceTest {

  private final ConsultSafetyService service = new ConsultSafetyService();

  @Test
  void shouldEscalateExplicitEmergencySymptoms() {
    ConsultDtos.SafetyInfo safety = service.assess("胸痛并且呼吸困难，需要继续运动吗？");

    assertEquals("emergency", safety.level());
    assertTrue(safety.flags().contains("CHEST_PAIN"));
    assertTrue(safety.flags().contains("BREATHING_DISTRESS"));
    assertTrue(safety.actionTags().contains("STOP_ACTIVITY"));
    assertTrue(safety.actionTags().contains("EMERGENCY_CARE"));
    assertTrue(service.requiresUrgentEscalation(safety));
  }

  @Test
  void shouldKeepRoutineHealthQuestionsOutOfEmergencyPath() {
    ConsultDtos.SafetyInfo safety = service.assess("最近睡眠不规律，如何调整作息？");

    assertEquals("routine", safety.level());
    assertTrue(safety.flags().isEmpty());
    assertTrue(safety.actionTags().contains("REASSESS_BEFORE_PROGRESSION"));
  }
}
