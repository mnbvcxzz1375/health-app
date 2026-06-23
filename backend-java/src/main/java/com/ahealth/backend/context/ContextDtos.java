package com.ahealth.backend.context;

import java.util.List;

public final class ContextDtos {
  private ContextDtos() {}

  public record ContextSnapshot(
      String systemSummary,
      String dailySummary,
      List<String> activeConcerns,
      List<String> currentMedications,
      List<MemoryEntry> memories,
      UserHealthBaseline healthBaseline,
      MedicationContextSummary medicationSummary,
      InteractionMemorySummary interactionSummary
  ) {}

  public record MemoryEntry(long id, String category, String content, String createdAt) {}

  public record SaveMemoryRequest(String category, String content) {}

  public record UserHealthBaseline(
      int restingHr,
      double avgSleepScore,
      double avgStressScore,
      double avgVo2Max,
      int avgSteps,
      int riskScore,
      String riskLevel
  ) {}

  public record MedicationContextSummary(
      int activeCount,
      List<String> medicationNames,
      List<String> warnings
  ) {}

  public record InteractionMemorySummary(
      int totalInteractions,
      List<String> recentTopics,
      String lastInteractionAt
  ) {}
}
