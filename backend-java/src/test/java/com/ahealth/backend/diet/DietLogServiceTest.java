package com.ahealth.backend.diet;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.ahealth.backend.common.ApiException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class DietLogServiceTest {
  @Mock
  private JdbcTemplate jdbcTemplate;

  @Test
  void rejectsMissingOrNegativeLogValues() {
    DietLogService service = new DietLogService(jdbcTemplate);
    assertThrows(ApiException.class, () -> service.save(7, null));
    assertThrows(ApiException.class, () -> service.save(7, new DietDtos.DietLogSaveRequest(
        "燕麦", "谷物", 50, -1, 8, 30, 3, "vision_food_catalog", null
    )));
  }

  @Test
  void savesAndReturnsTheCurrentUsersLog() {
    DietDtos.DietLogEntry entry = new DietDtos.DietLogEntry(
        11, "燕麦", "谷物", 50, 194.5, 8.5, 33.5, 3.5,
        "vision_food_catalog", LocalDateTime.now()
    );
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong())).thenReturn(List.of(entry));

    DietLogService service = new DietLogService(jdbcTemplate);
    DietDtos.DietLogEntry saved = service.save(7, new DietDtos.DietLogSaveRequest(
        "燕麦", "谷物", 50, 194.5, 8.5, 33.5, 3.5, "vision_food_catalog", null
    ));

    org.junit.jupiter.api.Assertions.assertEquals(11, saved.id());
    org.mockito.Mockito.verify(jdbcTemplate).update(anyString(), eq(7L), eq("燕麦"), eq("谷物"),
        eq(50.0), eq(194.5), eq(8.5), eq(33.5), eq(3.5), eq("vision_food_catalog"), any());
  }

  @Test
  void updatesOnlyTheOwningUsersLogAndWritesAudit() {
    DietDtos.DietLogEntry before = new DietDtos.DietLogEntry(
        11, "燕麦", "谷物", 50, 194.5, 8.5, 33.5, 3.5,
        "vision_food_catalog", LocalDateTime.now()
    );
    DietDtos.DietLogEntry after = new DietDtos.DietLogEntry(
        11, "糙米", "谷物", 80, 278.4, 6.4, 58.4, 2.4,
        "user_correction", before.recordedAt()
    );
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong(), anyLong()))
        .thenReturn(List.of(before), List.of(after));
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

    DietLogService service = new DietLogService(jdbcTemplate);
    DietDtos.DietLogEntry updated = service.update(7, 11, new DietDtos.DietLogSaveRequest(
        "糙米", "谷物", 80, 278.4, 6.4, 58.4, 2.4, "user_correction", null
    ));

    org.junit.jupiter.api.Assertions.assertEquals("糙米", updated.foodName());
    verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("diet_log_audits"),
        any(Object[].class));
  }

  @Test
  void deletesOwnedLogButKeepsAuditTrail() {
    DietDtos.DietLogEntry before = new DietDtos.DietLogEntry(
        11, "燕麦", "谷物", 50, 194.5, 8.5, 33.5, 3.5,
        "vision_food_catalog", LocalDateTime.now()
    );
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong(), anyLong()))
        .thenReturn(List.of(before));
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

    DietLogService service = new DietLogService(jdbcTemplate);
    DietDtos.DietLogOperationResult result = service.delete(7, 11);

    org.junit.jupiter.api.Assertions.assertTrue(result.success());
    verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("diet_logs"), eq(11L), eq(7L));
    verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("diet_log_audits"),
        any(Object[].class));
  }
}
