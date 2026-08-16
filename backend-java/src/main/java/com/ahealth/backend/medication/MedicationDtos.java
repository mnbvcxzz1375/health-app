package com.ahealth.backend.medication;

import java.util.List;

public final class MedicationDtos {
  private MedicationDtos() {}

  public record MedicationReminder(
      long id,
      String time,
      boolean enabled
  ) {}

  public record MedicationItem(
      long id,
      String name,
      String alias,
      int dosageValue,
      String dosageUnit,
      String usage,
      String notes,
      String photoUrl,
      boolean enableOcr,
      boolean enableYolo,
      String ocrEndpoint,
      String yoloEndpoint,
      boolean enabled,
      List<MedicationReminder> reminders,
      String medicineType,
      Long formulaId,
      Long clinicalInfoId
  ) {}

  public record MedicationReminderInput(
      String time,
      Boolean enabled
  ) {}

  public record MedicationSaveRequest(
      String name,
      String alias,
      Integer dosageValue,
      String dosageUnit,
      String usage,
      String notes,
      String photoUrl,
      Boolean enableOcr,
      Boolean enableYolo,
      String ocrEndpoint,
      String yoloEndpoint,
      Boolean enabled,
      List<MedicationReminderInput> reminders,
      String medicineType,
      Long formulaId,
      Long clinicalInfoId
  ) {}

  public record MedicationRecognitionResult(
      String name,
      String alias,
      Integer dosageValue,
      String dosageUnit,
      String usage,
      String notes,
      String photoUrl,
      Double confidence,
      String sourceText
  ) {}

  public record MedicationRecognitionBatchResult(
      List<MedicationRecognitionResult> items,
      Double confidence
  ) {}

  public record MedicationAlarmDrug(
      long id,
      String name,
      String alias,
      int dosageValue,
      String dosageUnit,
      String usage,
      String notes,
      String photoUrl,
      boolean enableOcr,
      boolean enableYolo,
      String ocrEndpoint,
      String yoloEndpoint,
      boolean enabled
  ) {}

  public record MedicationAlarmDrugInput(
      Long id,
      String name,
      String alias,
      Integer dosageValue,
      String dosageUnit,
      String usage,
      String notes,
      String photoUrl,
      Boolean enableOcr,
      Boolean enableYolo,
      String ocrEndpoint,
      String yoloEndpoint,
      Boolean enabled
  ) {}

  public record MedicationAlarm(
      long id,
      String time,
      boolean enabled,
      List<MedicationAlarmDrug> medications
  ) {}

  public record MedicationAlarmSaveRequest(
      String time,
      Boolean enabled,
      List<MedicationAlarmDrugInput> medications
  ) {}

  // === Phase 4: 药明白 DTOs ===

  public record MedicationExplainRequest(String name, String notes) {}

  public record MedicationExplainResponse(
      String clinicalParse,
      String elderFriendlyExplanation,
      List<String> warnings
  ) {}

  public record MedicationIntakeConfirmRequest(
      long alarmId,
      String status // taken | skipped | half
  ) {}

  public record TodayScheduleItem(
      long alarmId,
      String time,
      boolean enabled,
      List<MedicationAlarmDrug> medications,
      String intakeStatus // pending | taken | skipped | half | null
  ) {}

  public record TodayScheduleResponse(
      String date,
      List<TodayScheduleItem> items,
      int totalCount,
      int completedCount
  ) {}
}
