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
      InteractionMemorySummary interactionSummary,
      PatientMemoryBrief patientMemory
  ) {
    public ContextSnapshot(String systemSummary, String dailySummary, List<String> activeConcerns,
        List<String> currentMedications, List<MemoryEntry> memories, UserHealthBaseline healthBaseline,
        MedicationContextSummary medicationSummary, InteractionMemorySummary interactionSummary) {
      this(systemSummary, dailySummary, activeConcerns, currentMedications, memories, healthBaseline,
          medicationSummary, interactionSummary, PatientMemoryBrief.empty());
    }
  }

  public record MemoryEntry(long id, String category, String content, String createdAt) {}

  public record SaveMemoryRequest(String category, String content) {}

  public record PatientMemoryItem(
      long id,
      String tier,
      String memoryType,
      String content,
      String source,
      String safetyLevel,
      boolean confirmedByUser,
      boolean safetyCritical,
      String effectiveAt,
      String expiresAt
  ) {}

  public record PatientMemoryBrief(
      List<PatientMemoryItem> longTerm,
      List<PatientMemoryItem> careCycle,
      List<PatientMemoryItem> encounter,
      List<PatientMemoryItem> safetyFacts
  ) {
    public PatientMemoryBrief {
      longTerm = longTerm == null ? List.of() : List.copyOf(longTerm);
      careCycle = careCycle == null ? List.of() : List.copyOf(careCycle);
      encounter = encounter == null ? List.of() : List.copyOf(encounter);
      safetyFacts = safetyFacts == null ? List.of() : List.copyOf(safetyFacts);
    }

    public static PatientMemoryBrief empty() {
      return new PatientMemoryBrief(List.of(), List.of(), List.of(), List.of());
    }
  }

  public record SavePatientMemoryRequest(
      String memoryType,
      String content,
      String source,
      Boolean confirmedByUser,
      String safetyLevel
  ) {}

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
