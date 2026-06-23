package com.ahealth.backend.dashboard;

import java.util.List;

public final class HomeDtos {
  private HomeDtos() {}

  public record HomeMetric(
      String key,
      int value,
      String badge,
      String badgeVariant,
      String hint
  ) {}

  public record HomeSummaryResponse(
      String userName,
      int healthScore,
      String statusBadge,
      String statusBadgeVariant,
      String statusSummary,
      int stepsTarget,
      int stepsNow,
      List<HomeMetric> keyMetrics,
      List<String> suggestions
  ) {}
}
