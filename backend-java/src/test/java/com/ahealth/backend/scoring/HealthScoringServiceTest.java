package com.ahealth.backend.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.ahealth.backend.common.CurrentUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthScoringServiceTest {
  @Test
  void doesNotInventScoreWhenNoMonitoringRowsExist() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of());

    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(7L);
      ScoringDtos.HealthScoreResponse response = new HealthScoringService(jdbc).getScore();

      assertThat(response.overallScore()).isZero();
      assertThat(response.overallRisk()).isEqualTo("unknown");
      assertThat(response.dataQuality()).isEqualTo("none");
      assertThat(response.categoryScores()).isEmpty();
    }
  }

  @Test
  void marksSparseMonitoringRowsAsInsufficient() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    Map<String, Object> sparse = Map.of("hr", 72);
    when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of(sparse));

    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(7L);
      ScoringDtos.HealthScoreResponse response = new HealthScoringService(jdbc).getScore();

      assertThat(response.overallRisk()).isEqualTo("unknown");
      assertThat(response.dataQuality()).isEqualTo("insufficient");
      assertThat(response.dataWarnings()).isNotEmpty();
    }
  }

  @Test
  void partialRowsDoNotTreatMissingDimensionsAsNormal() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    Map<String, Object> partial = Map.of("hr", 72, "sleep_score", 70);
    when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of(partial));

    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(7L);
      ScoringDtos.HealthScoreResponse response = new HealthScoringService(jdbc).getScore();

      assertThat(response.dataQuality()).isEqualTo("partial");
      assertThat(response.categoryScores()).isNotEmpty();
      assertThat(response.categoryScores().stream()
          .filter(category -> "vo2Max".equals(category.key()))
          .findFirst().orElseThrow().dataAvailable()).isFalse();
      assertThat(response.categoryScores().stream()
          .filter(category -> "heartRate".equals(category.key()))
          .findFirst().orElseThrow().dataAvailable()).isTrue();
    }
  }
}
