package com.ahealth.backend.rehab;

import com.ahealth.backend.common.CurrentUser;
import com.ahealth.backend.context.ContextDtos;
import com.ahealth.backend.context.ContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Builds a bounded, user-scoped case view for rehabilitation planning. */
@Service
public class RehabCaseService {
  private final ContextService contextService;
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public RehabCaseService(ContextService contextService, JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper) {
    this.contextService = contextService;
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public RehabDtos.RehabCase buildCurrentCase(RehabDtos.SmartPlanRequest request) {
    long userId = CurrentUser.requireUserId();
    ContextDtos.ContextSnapshot snapshot = contextService.getSnapshot();
    ContextDtos.UserHealthBaseline baseline = snapshot.healthBaseline();
    ContextDtos.MedicationContextSummary medication = snapshot.medicationSummary();

    RehabDtos.CaseProfile profile = new RehabDtos.CaseProfile(
        positive(request.height()), positive(request.weight()), Math.max(0, request.age()),
        valueOr(request.gender(), "unknown"), valueOr(request.goal(), "maintenance"),
        valueOr(request.activityLevel(), "sedentary"), valueOr(request.source(), "manual"));
    RehabDtos.CaseMonitoring monitoring = new RehabDtos.CaseMonitoring(
        baseline.restingHr(), baseline.avgSleepScore(), baseline.avgStressScore(), baseline.avgVo2Max(),
        baseline.avgSteps(), baseline.riskScore(), baseline.riskLevel());
    RehabDtos.CaseMedication medications = new RehabDtos.CaseMedication(
        medication.activeCount(), safeList(medication.medicationNames()), safeList(medication.warnings()));
    List<RehabDtos.CaseReport> reports = loadSavedReports(userId);
    List<RehabDtos.CaseEvidence> evidence = buildEvidence(snapshot, reports);
    List<RehabDtos.PlanConstraint> constraints = buildConstraints(profile, monitoring, medications, reports,
        snapshot.patientMemory());
    RehabDtos.PlanSafety safety = buildSafety(constraints, evidence);

    String generatedAt = Instant.now().toString();
    String rangeFrom = Instant.parse(generatedAt).minus(30, ChronoUnit.DAYS).toString();
    return new RehabDtos.RehabCase(
        "rehab_case_" + UUID.randomUUID().toString().replace("-", ""),
        generatedAt, "rehab-case-v1", profile, monitoring, medications,
        reports, evidence, constraints, safety,
        new RehabDtos.CaseTimeRange("rolling_30_days", rangeFrom, generatedAt),
        new RehabDtos.CasePosture("not_available", null, List.of(),
            "posture-inference-service", ""));
  }

  public RehabDtos.RehabCase buildCurrentCase() {
    long userId = CurrentUser.requireUserId();
    try {
      List<Map<String, Object>> rows = jdbcTemplate.queryForList(
          "SELECT age, gender, height, weight, focus FROM user_settings WHERE user_id=?", userId);
      if (!rows.isEmpty()) {
        Map<String, Object> profile = rows.get(0);
        return buildCurrentCase(new RehabDtos.SmartPlanRequest(
            number(profile.get("height")), number(profile.get("weight")), (int) number(profile.get("age")),
            valueOr(profile.get("gender"), "unknown"), "rehab",
            "sedentary", "profile"));
      }
    } catch (Exception ignored) {
      // A case with partial evidence is still more honest than an invented profile.
    }
    return buildCurrentCase(new RehabDtos.SmartPlanRequest(0, 0, 0, "unknown", "rehab", "sedentary", "profile"));
  }

  private List<RehabDtos.CaseEvidence> buildEvidence(ContextDtos.ContextSnapshot snapshot,
      List<RehabDtos.CaseReport> reports) {
    List<RehabDtos.CaseEvidence> evidence = new ArrayList<>();
    ContextDtos.UserHealthBaseline baseline = snapshot.healthBaseline();
    if (!"暂无监测数据".equals(snapshot.dailySummary())) {
      evidence.add(new RehabDtos.CaseEvidence(
          "monitoring-30d", "monitoring_baseline",
          "30-day baseline: resting HR=" + baseline.restingHr() + ", sleep="
              + baseline.avgSleepScore() + ", stress=" + baseline.avgStressScore(), "rolling_30_days"));
    }
    if (snapshot.medicationSummary().activeCount() > 0) {
      evidence.add(new RehabDtos.CaseEvidence(
          "medications-active", "medication_list", "Active medications="
              + snapshot.medicationSummary().activeCount(), "current"));
    }
    for (RehabDtos.CaseReport report : reports) {
      evidence.add(new RehabDtos.CaseEvidence(
          "report-" + report.taskId(), "saved_report", valueOr(report.title(), "Saved health report"),
          report.updatedAt()));
    }
    ContextDtos.PatientMemoryBrief patientMemory = snapshot.patientMemory();
    if (patientMemory != null) {
      addMemoryEvidence(evidence, patientMemory.longTerm(), "long_term_memory");
      addMemoryEvidence(evidence, patientMemory.careCycle(), "care_cycle_memory");
    }
    return evidence;
  }

  private List<RehabDtos.PlanConstraint> buildConstraints(RehabDtos.CaseProfile profile,
      RehabDtos.CaseMonitoring monitoring, RehabDtos.CaseMedication medication,
      List<RehabDtos.CaseReport> reports, ContextDtos.PatientMemoryBrief patientMemory) {
    List<RehabDtos.PlanConstraint> constraints = new ArrayList<>();
    if (profile.age() <= 0 || profile.height() <= 0 || profile.weight() <= 0) {
      constraints.add(new RehabDtos.PlanConstraint("INCOMPLETE_PROFILE", "medium",
          "Age, height, or weight is missing from the current profile.",
          "Do not calculate a personalized calorie or intensity baseline; collect the missing profile fields before progression."));
    }
    if (monitoring.riskScore() >= 60 || "high".equalsIgnoreCase(monitoring.riskLevel())) {
      constraints.add(new RehabDtos.PlanConstraint("PROFILE_HIGH_RISK", "high",
          "The profile is marked as high risk.", "Use only low-intensity, supervised-safe movements and seek clinician review."));
    }
    if (monitoring.restingHeartRate() > 90) {
      constraints.add(new RehabDtos.PlanConstraint("HIGH_RESTING_HEART_RATE", "medium",
          "Recent resting heart-rate baseline is above the conservative planning threshold.",
          "Avoid interval or high-intensity work; stop if palpitations, dizziness, chest discomfort, or breathlessness occur."));
    }
    if (monitoring.sleepScore() > 0 && monitoring.sleepScore() < 70) {
      constraints.add(new RehabDtos.PlanConstraint("LOW_SLEEP_RECOVERY", "medium",
          "Recent sleep baseline suggests incomplete recovery.", "Shorten the session and prioritize mobility, breathing, and technique."));
    }
    if (monitoring.stressScore() > 65) {
      constraints.add(new RehabDtos.PlanConstraint("HIGH_STRESS", "medium",
          "Recent stress baseline is elevated.", "Avoid adding intensity or volume until recovery is reassessed."));
    }
    if (medication.activeCount() > 0) {
      constraints.add(new RehabDtos.PlanConstraint("ACTIVE_MEDICATION_REVIEW", "info",
          "Active medication information is present.", "Do not infer dosage changes from the plan; confirm exercise precautions with a clinician or pharmacist."));
    }
    if (reports.stream().anyMatch(report -> "high".equalsIgnoreCase(report.riskLevel()))) {
      constraints.add(new RehabDtos.PlanConstraint("SAVED_REPORT_HIGH_RISK", "high",
          "A saved report contains a high-risk label.", "Do not advance training intensity before offline professional review."));
    }
    if (patientMemory != null && !patientMemory.safetyFacts().isEmpty()) {
      String facts = String.join("；", patientMemory.safetyFacts().stream()
          .map(item -> item.memoryType() + ": " + item.content()).toList());
      constraints.add(new RehabDtos.PlanConstraint("CONFIRMED_LONG_TERM_SAFETY_MEMORY", "medium",
          "Confirmed long-term safety memory is present: " + facts,
          "Keep these confirmed safety facts visible when selecting exercise intensity or discussing medication; do not recommend a conflicting action without professional review."));
    }
    return constraints;
  }

  private void addMemoryEvidence(List<RehabDtos.CaseEvidence> evidence,
      List<ContextDtos.PatientMemoryItem> memories, String sourceType) {
    if (memories == null) return;
    for (ContextDtos.PatientMemoryItem memory : memories) {
      evidence.add(new RehabDtos.CaseEvidence("memory-" + memory.id(), sourceType,
          memory.memoryType() + ": " + memory.content(), memory.effectiveAt()));
    }
  }

  private RehabDtos.PlanSafety buildSafety(List<RehabDtos.PlanConstraint> constraints,
      List<RehabDtos.CaseEvidence> evidence) {
    boolean constrained = constraints.stream().anyMatch(item ->
        "high".equalsIgnoreCase(item.level()) || "medium".equalsIgnoreCase(item.level()));
    boolean noEvidence = evidence.isEmpty();
    List<String> flags = new ArrayList<>(constraints.stream().map(RehabDtos.PlanConstraint::code).toList());
    List<String> actionTags = buildActionTags(constraints, evidence);
    if (noEvidence) addTag(flags, "NO_CASE_EVIDENCE");
    return new RehabDtos.PlanSafety(
        constrained ? "elevated" : noEvidence ? "uncertain" : "routine", flags,
        noEvidence
            ? "No usable case evidence was available; this plan must not be treated as personalized medical guidance."
            : "The plan uses only the listed health-management data and cannot replace in-person assessment.",
        constrained
            ? "Collect missing profile data and seek clinician review before progressing exercise intensity."
            : noEvidence ? "Collect monitoring or professional evidence before personalizing the plan."
                : "Stop activity and seek medical advice if new or worsening symptoms occur.",
        actionTags);
  }

  private List<String> buildActionTags(List<RehabDtos.PlanConstraint> constraints,
      List<RehabDtos.CaseEvidence> evidence) {
    List<String> tags = new ArrayList<>();
    for (RehabDtos.PlanConstraint constraint : constraints) {
      switch (constraint.code()) {
        case "INCOMPLETE_PROFILE" -> {
          addTag(tags, "NO_PERSONALIZED_GUIDANCE");
          addTag(tags, "REQUEST_MORE_EVIDENCE");
          addTag(tags, "LOWER_INTENSITY");
        }
        case "PROFILE_HIGH_RISK", "SAVED_REPORT_HIGH_RISK" -> {
          addTag(tags, "HOLD_PROGRESSION");
          addTag(tags, "PROFESSIONAL_REVIEW");
        }
        case "HIGH_RESTING_HEART_RATE" -> {
          addTag(tags, "LOWER_INTENSITY");
          addTag(tags, "REASSESS_BEFORE_PROGRESSION");
        }
        case "LOW_SLEEP_RECOVERY" -> {
          addTag(tags, "SHORTEN_SESSION");
          addTag(tags, "RECOVERY_PRIORITY");
        }
        case "HIGH_STRESS" -> addTag(tags, "RECOVERY_PRIORITY");
        case "ACTIVE_MEDICATION_REVIEW" -> {
          addTag(tags, "NO_DOSAGE_INFERENCE");
          addTag(tags, "PHARMACIST_OR_CLINICIAN_REVIEW");
        }
        case "CONFIRMED_LONG_TERM_SAFETY_MEMORY" -> {
          addTag(tags, "RESPECT_CONFIRMED_SAFETY_MEMORY");
          addTag(tags, "PROFESSIONAL_REVIEW");
        }
        default -> { }
      }
    }
    if (evidence.isEmpty()) {
      addTag(tags, "NO_PERSONALIZED_GUIDANCE");
      addTag(tags, "REQUEST_MORE_EVIDENCE");
    }
    return List.copyOf(tags);
  }

  private void addTag(List<String> tags, String tag) {
    if (!tags.contains(tag)) tags.add(tag);
  }

  private List<RehabDtos.CaseReport> loadSavedReports(long userId) {
    try {
      List<Map<String, Object>> rows = jdbcTemplate.queryForList(
          "SELECT id, type, report_json, updated_at FROM analyze_tasks "
              + "WHERE user_id=? AND saved=1 AND status='DONE' ORDER BY updated_at DESC LIMIT 3", userId);
      List<RehabDtos.CaseReport> reports = new ArrayList<>();
      for (Map<String, Object> row : rows) {
        JsonNode report = objectMapper.readTree(valueOr(row.get("report_json"), "{}"));
        reports.add(new RehabDtos.CaseReport(valueOr(row.get("id"), ""), valueOr(row.get("type"), ""),
            report.path("title").asText("Saved health report"), report.path("riskLevel").asText("unknown"),
            valueOr(row.get("updated_at"), "")));
      }
      return reports;
    } catch (Exception ignored) {
      return List.of();
    }
  }

  private double positive(double value) { return value > 0 ? value : 0; }

  private double number(Object value) { return value instanceof Number number ? number.doubleValue() : 0; }

  private String valueOr(Object value, String fallback) {
    String text = value == null ? "" : String.valueOf(value).trim();
    return text.isBlank() ? fallback : text;
  }

  private List<String> safeList(List<String> values) { return values == null ? List.of() : List.copyOf(values); }
}
