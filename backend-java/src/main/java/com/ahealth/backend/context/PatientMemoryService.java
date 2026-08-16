package com.ahealth.backend.context;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Stores clinically meaningful memory separately from raw chat history.
 * Long-term records require user confirmation; time-sensitive records expire automatically.
 */
@Service
public class PatientMemoryService {
  private static final int LONG_TERM_LIMIT = 12;
  private static final int CARE_CYCLE_LIMIT = 8;
  private static final int ENCOUNTER_LIMIT = 4;

  private final JdbcTemplate jdbc;

  public PatientMemoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public ContextDtos.PatientMemoryBrief getBrief() {
    return getBrief(CurrentUser.requireUserId());
  }

  public ContextDtos.PatientMemoryBrief getBrief(long userId) {
    expireDueItems(userId);
    List<ContextDtos.PatientMemoryItem> items = jdbc.queryForList(
        "SELECT id,memory_tier,memory_type,content,source,safety_level,confirmed_by_user,"
            + "effective_at,expires_at FROM patient_memory_items "
            + "WHERE user_id=? AND status='active' AND (expires_at IS NULL OR expires_at>NOW()) "
            + "ORDER BY CASE memory_tier WHEN 'long_term' THEN 0 WHEN 'care_cycle' THEN 1 ELSE 2 END, "
            + "CASE safety_level WHEN 'critical' THEN 0 WHEN 'high' THEN 1 WHEN 'elevated' THEN 2 ELSE 3 END, "
            + "confirmed_by_user DESC, updated_at DESC",
        userId).stream().map(this::mapItem).toList();

    List<ContextDtos.PatientMemoryItem> longTerm = limited(items, "long_term", LONG_TERM_LIMIT);
    List<ContextDtos.PatientMemoryItem> careCycle = limited(items, "care_cycle", CARE_CYCLE_LIMIT);
    List<ContextDtos.PatientMemoryItem> encounter = limited(items, "encounter", ENCOUNTER_LIMIT);
    List<ContextDtos.PatientMemoryItem> safetyFacts = longTerm.stream()
        .filter(ContextDtos.PatientMemoryItem::safetyCritical).toList();
    return new ContextDtos.PatientMemoryBrief(longTerm, careCycle, encounter, safetyFacts);
  }

  public ContextDtos.PatientMemoryItem save(ContextDtos.SavePatientMemoryRequest request) {
    long userId = CurrentUser.requireUserId();
    return save(userId, request);
  }

  ContextDtos.PatientMemoryItem save(long userId, ContextDtos.SavePatientMemoryRequest request) {
    String type = PatientMemoryPolicy.normalizeType(request.memoryType());
    String content = request.content() == null ? "" : request.content().trim();
    if (content.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Memory content cannot be blank.");
    }
    if (content.length() > 500) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Memory content must be at most 500 characters.");
    }
    PatientMemoryPolicy.Rule rule;
    try {
      rule = PatientMemoryPolicy.ruleFor(type);
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
    boolean confirmed = Boolean.TRUE.equals(request.confirmedByUser());
    if (rule.requiresConfirmation() && !confirmed) {
      throw new ApiException(HttpStatus.BAD_REQUEST,
          "Long-term medical memory must be confirmed by the user before it is saved.");
    }

    String source = sourceOrDefault(request.source(), "user_confirmed");
    String safetyLevel = resolveSafetyLevel(rule, request.safetyLevel());
    Timestamp expiresAt = rule.retention() == null ? null
        : Timestamp.from(Instant.now().plus(rule.retention()).truncatedTo(ChronoUnit.SECONDS));

    // A new statement of the same type supersedes older active statements instead of silently mixing facts.
    jdbc.update("UPDATE patient_memory_items SET status='superseded', superseded_at=NOW() "
            + "WHERE user_id=? AND memory_type=? AND status='active'",
        userId, type);
    jdbc.update("INSERT INTO patient_memory_items(user_id,memory_tier,memory_type,content,source,"
            + "safety_level,confirmed_by_user,status,effective_at,expires_at,created_at,updated_at) "
            + "VALUES(?,?,?,?,?,?,?,'active',NOW(),?,NOW(),NOW())",
        userId, rule.tier().value(), type, content, source, safetyLevel, confirmed, expiresAt);
    Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return findById(userId, id == null ? 0L : id);
  }

  public void retire(long id) {
    long userId = CurrentUser.requireUserId();
    jdbc.update("UPDATE patient_memory_items SET status='retired', superseded_at=NOW() "
        + "WHERE id=? AND user_id=? AND status='active'", id, userId);
  }

  public void recordConsultSummary(String question, String answer) {
    String safeQuestion = truncate(question, 90);
    String safeAnswer = truncate(answer, 140);
    if (safeQuestion.isBlank() && safeAnswer.isBlank()) return;
    String content = "Question: " + safeQuestion + " | Answer summary: " + safeAnswer;
    saveEncounter(CurrentUser.requireUserId(), "consult_summary", content, "consult");
  }

  public String toPromptContext(ContextDtos.PatientMemoryBrief brief) {
    StringBuilder prompt = new StringBuilder();
    appendTier(prompt, "Confirmed long-term safety memory", brief.longTerm());
    appendTier(prompt, "Current rehabilitation-cycle memory", brief.careCycle());
    appendTier(prompt, "Recent encounter memory", brief.encounter());
    return prompt.toString();
  }

  private void saveEncounter(long userId, String type, String content, String source) {
    PatientMemoryPolicy.Rule rule = PatientMemoryPolicy.ruleFor(type);
    jdbc.update("INSERT INTO patient_memory_items(user_id,memory_tier,memory_type,content,source,"
            + "safety_level,confirmed_by_user,status,effective_at,expires_at,created_at,updated_at) "
            + "VALUES(?,?,?,?,?,'routine',FALSE,'active',NOW(),DATE_ADD(NOW(), INTERVAL 7 DAY),NOW(),NOW())",
        userId, rule.tier().value(), type, content, source);
    jdbc.update("DELETE FROM patient_memory_items WHERE user_id=? AND memory_type='consult_summary' "
            + "AND status='active' AND id NOT IN (SELECT id FROM (SELECT id FROM patient_memory_items "
            + "WHERE user_id=? AND memory_type='consult_summary' AND status='active' ORDER BY updated_at DESC LIMIT 20) recent)"
        , userId, userId);
  }

  private ContextDtos.PatientMemoryItem findById(long userId, long id) {
    List<ContextDtos.PatientMemoryItem> items = jdbc.queryForList(
        "SELECT id,memory_tier,memory_type,content,source,safety_level,confirmed_by_user,effective_at,expires_at "
            + "FROM patient_memory_items WHERE user_id=? AND id=?", userId, id)
        .stream().map(this::mapItem).toList();
    if (items.isEmpty()) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Memory save failed.");
    return items.get(0);
  }

  private ContextDtos.PatientMemoryItem mapItem(Map<String, Object> row) {
    String type = text(row.get("memory_type"));
    boolean safety = PatientMemoryPolicy.ruleFor(type).safetyCritical();
    return new ContextDtos.PatientMemoryItem(
        longValue(row.get("id")), text(row.get("memory_tier")), type, text(row.get("content")),
        text(row.get("source")), text(row.get("safety_level")), boolValue(row.get("confirmed_by_user")),
        safety, text(row.get("effective_at")), text(row.get("expires_at")));
  }

  private List<ContextDtos.PatientMemoryItem> limited(List<ContextDtos.PatientMemoryItem> items,
      String tier, int limit) {
    return items.stream().filter(item -> tier.equals(item.tier())).limit(limit).toList();
  }

  private void expireDueItems(long userId) {
    jdbc.update("UPDATE patient_memory_items SET status='expired', superseded_at=NOW() "
        + "WHERE user_id=? AND status='active' AND expires_at IS NOT NULL AND expires_at<=NOW()", userId);
  }

  private void appendTier(StringBuilder prompt, String heading, List<ContextDtos.PatientMemoryItem> items) {
    if (items == null || items.isEmpty()) return;
    prompt.append(heading).append(": ");
    prompt.append(String.join("; ", items.stream().map(item -> "[" + item.memoryType() + "] " + item.content()).toList()));
    prompt.append("\n");
  }

  private String resolveSafetyLevel(PatientMemoryPolicy.Rule rule, String raw) {
    if (rule.safetyCritical()) return "elevated";
    String level = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    return switch (level) {
      case "routine", "elevated", "high", "critical" -> level;
      default -> "routine";
    };
  }

  private String sourceOrDefault(String source, String fallback) {
    String value = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
    return value.isBlank() ? fallback : truncate(value, 32);
  }

  private String truncate(String value, int max) {
    String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
    return text.length() <= max ? text : text.substring(0, max) + "…";
  }

  private String text(Object value) { return value == null ? "" : String.valueOf(value); }
  private long longValue(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
  private boolean boolValue(Object value) {
    return value instanceof Boolean b ? b : value instanceof Number n && n.intValue() != 0;
  }
}
