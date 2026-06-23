package com.ahealth.backend.device;

import java.util.List;
import java.util.Map;

public final class RookDtos {
  private RookDtos() {}

  /** Data source authorization status */
  public record DataSourceAuth(
      String dataSource,
      boolean authorized,
      String authorizationUrl
  ) {}

  /** All authorized data sources */
  public record AuthorizedSources(
      String userId,
      Map<String, Boolean> sources
  ) {}

  /** Physical health summary from ROOK */
  public record PhysicalHealthSummary(
      ActivityData activity,
      CaloriesData calories,
      DistanceData distance,
      HeartRateData heartRate,
      OxygenationData oxygenation,
      StressData stress
  ) {
    public record ActivityData(
        int activeSeconds, int inactiveSeconds, int restSeconds,
        int highIntensitySeconds, int mediumIntensitySeconds, int lowIntensitySeconds
    ) {}
    public record CaloriesData(
        double bmrKcal, double expenditureKcal, double netActiveKcal
    ) {}
    public record DistanceData(
        int steps, int activeSteps, int floorsClimbed, double elevationMeters
    ) {}
    public record HeartRateData(
        double avgBpm, double maxBpm, double minBpm, double restingBpm,
        double hrvAvgRmssd, double hrvAvgSdnn
    ) {}
    public record OxygenationData(double avgSpo2, double vo2Max) {}
    public record StressData(
        double avgLevel, double maxLevel,
        int highStressDurationSeconds, int mediumStressDurationSeconds,
        int lowStressDurationSeconds, int restDurationSeconds
    ) {}
  }

  /** Sleep health summary from ROOK */
  public record SleepHealthSummary(
      DurationData duration,
      ScoresData scores,
      PhysicalHealthSummary.HeartRateData heartRate,
      BreathingData breathing
  ) {
    public record DurationData(
        String sleepStart, String sleepEnd,
        int totalSleepSeconds, int timeInBedSeconds,
        int lightSleepSeconds, int remSleepSeconds, int deepSleepSeconds,
        int timeToFallAsleepSeconds, int timeAwakeDuringSleepSeconds
    ) {}
    public record ScoresData(
        int qualityRating, int efficiency, int continuityScore
    ) {}
    public record BreathingData(
        double breathsAvgPerMin, int snoringEventsCount, double spo2Avg
    ) {}
  }

  /** Activity event from ROOK */
  public record ActivityEvent(
      String activityType, int durationSeconds,
      String startDatetime, String endDatetime,
      double strainLevel,
      PhysicalHealthSummary.HeartRateData heartRate,
      MovementData movement
  ) {
    public record MovementData(
        int steps, double avgPace, double maxPace, double avgSpeed
    ) {}
  }

  /** User information from ROOK */
  public record RookUserInfo(
      double heightCm, double weightKg, double bmi,
      String sex, String dateOfBirth
  ) {}

  /** Rehab analysis result combining device data with rehab plan */
  public record RehabAnalysisResult(
      String exerciseName,
      String performanceAssessment,  // "good", "overexertion", "underperformance", "no_data"
      int actualDurationSeconds,
      int targetDurationSeconds,
      double exertionLevel,          // 0-1 scale
      List<String> warnings,
      List<String> recommendations
  ) {}
}
