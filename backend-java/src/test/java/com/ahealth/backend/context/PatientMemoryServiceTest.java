package com.ahealth.backend.context;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.ahealth.backend.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PatientMemoryServiceTest {

  @Test
  void rejectsUnconfirmedLongTermSafetyFactBeforeWritingIt() {
    PatientMemoryService service = new PatientMemoryService(mock(JdbcTemplate.class));
    ContextDtos.SavePatientMemoryRequest request = new ContextDtos.SavePatientMemoryRequest(
        "allergy", "对青霉素过敏", "user_reported", false, "high");

    assertThrows(ApiException.class, () -> service.save(8L, request));
  }
}
