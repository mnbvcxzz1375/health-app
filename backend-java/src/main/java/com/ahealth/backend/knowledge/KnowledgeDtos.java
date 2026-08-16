package com.ahealth.backend.knowledge;

import java.time.LocalDateTime;
import java.util.List;

public final class KnowledgeDtos {
  private KnowledgeDtos() {}

  // ===== 方剂 =====

  public record FormulaHerbInput(String herbName, Integer dosageGrams, String role) {}

  public record FormulaSaveRequest(String name, String diagnosis, List<FormulaHerbInput> herbs, String notes) {}

  public record FormulaHerbItem(
      long herbId,
      String herbName,
      String pinyin,
      String nature,
      String flavor,
      String meridian,
      String efficacy,
      Integer dosageGrams,
      String role
  ) {}

  public record FormulaResponse(
      long id,
      String name,
      String diagnosis,
      List<FormulaHerbItem> herbs,
      String notes,
      LocalDateTime createdAt
  ) {}

  public record FormulaListItem(
      long id,
      String name,
      int herbCount,
      LocalDateTime createdAt
  ) {}

  // ===== 多药材识别 =====

  public record HerbRecognitionItem(
      String herbName,
      String pinyin,
      String nature,
      String flavor,
      String meridian,
      String efficacy,
      Double confidence,
      String source
  ) {}

  public record HerbRecognitionResult(
      List<HerbRecognitionItem> items,
      List<String> duplicatesRemoved,
      Double confidence
  ) {}

  // ===== 药品临床信息 =====

  public record ClinicalInfoResponse(
      String drugName,
      String medicineType,
      List<String> ingredients,
      String indications,
      List<String> sideEffects,
      List<String> allergicReactions,
      List<String> contraindicatedGroups,
      String contraindications,
      List<String> interactions,
      List<String> dietaryTaboos,
      Integer dosingIntervalMinutes,
      String source
  ) {}

  // ===== 交互报告 =====

  public record InteractionRecord(
      String type,
      String severity,
      String drugA,
      String drugB,
      String description,
      String source
  ) {}

  public record InteractionReport(
      List<InteractionRecord> tcmIncompatibilities,
      List<InteractionRecord> tcmWmInteractions,
      List<InteractionRecord> drugFoodInteractions,
      List<InteractionRecord> ddiWarnings,
      List<InteractionRecord> allergyConflicts,
      List<InteractionRecord> contraindicatedGroupWarnings,
      int totalWarnings,
      List<String> summary
  ) {}

  // ===== 用药间隔 =====

  public record DosingScheduleItem(
      String drugName,
      String medicineType,
      String suggestedTime,
      Integer intervalMinutes,
      String reason
  ) {}

  public record DosingSchedule(
      String date,
      List<DosingScheduleItem> morning,
      List<DosingScheduleItem> noon,
      List<DosingScheduleItem> evening,
      List<String> notes
  ) {}

  // ===== 过敏 =====

  public record AllergySaveRequest(String allergen, String allergenType, String severity, String note) {}

  public record AllergyItem(
      long id,
      String allergen,
      String allergenType,
      String severity,
      String note
  ) {}

  // ===== 中药材搜索 =====

  public record HerbSearchItem(
      long id,
      String name,
      String pinyin,
      String alias,
      String nature,
      String flavor,
      String meridian,
      String efficacy
  ) {}
}
