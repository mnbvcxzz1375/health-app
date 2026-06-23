package com.ahealth.backend.scoring;

import java.util.List;

public final class ScoringDtos {
  private ScoringDtos() {}

  public record HealthScoreResponse(
      int overallScore,
      String overallRisk,
      List<CategoryScore> categoryScores,
      List<TopRisk> topRisks,
      List<String> recommendedActions,
      String summary
  ) {}

  public record CategoryScore(
      String key,
      String label,
      int score,
      double currentValue,
      double baselineValue,
      double offset,
      String riskNote,
      String attentionType,
      double weight
  ) {}

  public record TopRisk(
      String attentionType,
      String label,
      String description,
      int severity
  ) {}
}
