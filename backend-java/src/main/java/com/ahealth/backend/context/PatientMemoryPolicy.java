package com.ahealth.backend.context;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/** Defines retention and confirmation rules for patient-scoped memory. */
public final class PatientMemoryPolicy {
  private PatientMemoryPolicy() {}

  public enum Tier {
    LONG_TERM("long_term"), CARE_CYCLE("care_cycle"), ENCOUNTER("encounter");

    private final String value;
    Tier(String value) { this.value = value; }
    public String value() { return value; }
  }

  public record Rule(Tier tier, Duration retention, boolean requiresConfirmation,
                     boolean safetyCritical) {}

  private static final Set<String> LONG_TERM_SAFETY = Set.of(
      "chronic_condition", "allergy", "contraindication", "functional_limit");
  private static final Set<String> LONG_TERM = Set.of(
      "chronic_condition", "allergy", "contraindication", "functional_limit", "care_preference");
  private static final Set<String> CARE_CYCLE = Set.of(
      "rehab_goal", "rehab_phase", "monitoring_baseline", "adherence_pattern", "follow_up_plan");
  private static final Set<String> ENCOUNTER = Set.of(
      "symptom_episode", "rehab_session_feedback", "consult_summary", "pending_question");

  public static Rule ruleFor(String rawType) {
    String type = normalizeType(rawType);
    if (LONG_TERM.contains(type)) {
      return new Rule(Tier.LONG_TERM, null, true, LONG_TERM_SAFETY.contains(type));
    }
    if (CARE_CYCLE.contains(type)) {
      return new Rule(Tier.CARE_CYCLE, Duration.ofDays(90), false, false);
    }
    if (ENCOUNTER.contains(type)) {
      return new Rule(Tier.ENCOUNTER, "symptom_episode".equals(type)
          ? Duration.ofDays(14) : Duration.ofDays(7), false, false);
    }
    throw new IllegalArgumentException("Unsupported patient memory type: " + rawType);
  }

  public static String normalizeType(String rawType) {
    if (rawType == null) return "";
    return rawType.trim().toLowerCase(Locale.ROOT);
  }
}
