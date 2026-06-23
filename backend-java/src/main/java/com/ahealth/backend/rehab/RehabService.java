package com.ahealth.backend.rehab;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import com.ahealth.backend.common.JsonSupport;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RehabService {
  private static final List<String> VALID_REMINDER_DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");
  private static final List<String> DEFAULT_REMINDER_DAYS = List.of("mon", "wed", "fri");

  private final JdbcTemplate jdbcTemplate;
  private final JsonSupport jsonSupport;

  public RehabService(JdbcTemplate jdbcTemplate, JsonSupport jsonSupport) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonSupport = jsonSupport;
  }

  public RehabDtos.RehabPlanResponse getPlan() {
    long userId = CurrentUser.requireUserId();
    ensureTodayPlan(userId);
    return buildPlan(userId);
  }

  @Transactional
  public RehabDtos.RehabPlanResponse togglePlanItem(long id) {
    long userId = CurrentUser.requireUserId();
    int updated = jdbcTemplate.update(
        """
        UPDATE rehab_plan_items
        SET done = CASE WHEN done = 1 THEN 0 ELSE 1 END
        WHERE id = ? AND user_id = ?
        """,
        id,
        userId
    );
    if (updated == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "训练记录不存在");
    }
    return buildPlan(userId);
  }

  @Transactional
  public RehabDtos.RehabPlanResponse removePlanItem(long id) {
    long userId = CurrentUser.requireUserId();
    int updated = jdbcTemplate.update(
        "DELETE FROM rehab_plan_items WHERE id = ? AND user_id = ?",
        id,
        userId
    );
    if (updated == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "训练记录不存在");
    }
    return buildPlan(userId);
  }

  @Transactional
  public RehabDtos.RehabPlanResponse applyPlanDraft(RehabDtos.RehabPlanDraft request) {
    long userId = CurrentUser.requireUserId();
    validateDraft(request);

    List<Long> exerciseIds = request.exercises().stream()
        .map(candidate -> resolveDraftExerciseId(userId, candidate))
        .toList();

    savePlanSummaryInternal(userId, request.summary());
    savePlanReminderInternal(userId, request.reminder());
    replaceTodayPlanItems(userId, exerciseIds);
    return buildPlan(userId);
  }

  public RehabDtos.RehabExercise getExerciseByName(String name) {
    long userId = CurrentUser.requireUserId();
    List<RehabDtos.RehabExercise> rows = jdbcTemplate.query(
        """
        SELECT id, name, category, duration, level, minutes, steps_json, caution, focus, benefits_json, video_minutes
        FROM rehab_exercises
        WHERE name = ? AND (user_id IS NULL OR user_id = ?)
        ORDER BY CASE WHEN user_id = ? THEN 0 ELSE 1 END, id ASC
        LIMIT 1
        """,
        (rs, rowNum) -> new RehabDtos.RehabExercise(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getString("duration"),
            rs.getString("level"),
            rs.getInt("minutes"),
            jsonSupport.readStringList(rs.getString("steps_json")),
            rs.getString("caution"),
            rs.getString("focus"),
            jsonSupport.readStringList(rs.getString("benefits_json")),
            rs.getInt("video_minutes"),
            false
        ),
        name,
        userId,
        userId
    );
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "未找到动作");
    }
    return rows.get(0);
  }

  public RehabDtos.RehabReminderResponse getReminder(String name) {
    long userId = CurrentUser.requireUserId();
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        """
        SELECT exercise_name, reminder_time, days_json, push_enabled
        FROM rehab_reminders
        WHERE user_id = ? AND exercise_name = ?
        LIMIT 1
        """,
        userId,
        name
    );
    if (rows.isEmpty()) {
      return new RehabDtos.RehabReminderResponse(name, "08:00", DEFAULT_REMINDER_DAYS, true);
    }
    Map<String, Object> row = rows.get(0);
    return new RehabDtos.RehabReminderResponse(
        String.valueOf(row.get("exercise_name")),
        String.valueOf(row.get("reminder_time")),
        jsonSupport.readStringList(String.valueOf(row.get("days_json"))),
        boolValue(row.get("push_enabled"))
    );
  }

  @Transactional
  public RehabDtos.RehabReminderResponse saveReminder(RehabDtos.SaveRehabReminderRequest request) {
    long userId = CurrentUser.requireUserId();
    if (request.name() == null || request.name().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "动作名称不能为空");
    }
    jdbcTemplate.update(
        """
        INSERT INTO rehab_reminders (user_id, exercise_name, reminder_time, days_json, push_enabled)
        VALUES (?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          reminder_time = VALUES(reminder_time),
          days_json = VALUES(days_json),
          push_enabled = VALUES(push_enabled)
        """,
        userId,
        request.name().trim(),
        normalizeReminderTime(request.time()),
        jsonSupport.write(normalizeReminderDays(request.days())),
        request.pushEnabled() == null || request.pushEnabled() ? 1 : 0
    );
    return getReminder(request.name().trim());
  }

  public RehabDtos.RehabPlanSettingsResponse getPlanSettings() {
    RehabDtos.RehabPlanSummary summary = getPlanSummaryInternal(CurrentUser.requireUserId());
    return new RehabDtos.RehabPlanSettingsResponse(
        summary.focus(),
        summary.frequency(),
        summary.duration(),
        summary.intensity()
    );
  }

  @Transactional
  public RehabDtos.RehabPlanSettingsResponse savePlanSettings(RehabDtos.RehabPlanSettingsResponse request) {
    long userId = CurrentUser.requireUserId();
    RehabDtos.RehabPlanSummary normalized = normalizePlanSummary(request.focus(), request.frequency(), request.duration(), request.intensity());
    savePlanSummaryInternal(userId, normalized);
    return new RehabDtos.RehabPlanSettingsResponse(
        normalized.focus(),
        normalized.frequency(),
        normalized.duration(),
        normalized.intensity()
    );
  }

  public RehabDtos.PlanReminderDraft getPlanReminder() {
    long userId = CurrentUser.requireUserId();
    Map<String, Object> row = fetchPlanReminderRow(userId);
    if (row == null) {
      return new RehabDtos.PlanReminderDraft("08:00", DEFAULT_REMINDER_DAYS, true);
    }
    return new RehabDtos.PlanReminderDraft(
        String.valueOf(row.get("reminder_time")),
        jsonSupport.readStringList(String.valueOf(row.get("days_json"))),
        boolValue(row.get("push_enabled"))
    );
  }

  @Transactional
  public RehabDtos.PlanReminderDraft savePlanReminder(RehabDtos.PlanReminderDraft request) {
    long userId = CurrentUser.requireUserId();
    RehabDtos.PlanReminderDraft normalized = new RehabDtos.PlanReminderDraft(
        normalizeReminderTime(request.time()),
        normalizeReminderDays(request.days()),
        request.pushEnabled()
    );
    savePlanReminderInternal(userId, normalized);
    return normalized;
  }

  private RehabDtos.RehabPlanResponse buildPlan(long userId) {
    List<RehabDtos.RehabExercise> exercises = jdbcTemplate.query(
        """
        SELECT rpi.id AS plan_id, rpi.done, re.name, re.category, re.duration, re.level,
               re.minutes, re.steps_json, re.caution, re.focus, re.benefits_json, re.video_minutes
        FROM rehab_plan_items rpi
        JOIN rehab_exercises re ON re.id = rpi.exercise_id
        WHERE rpi.user_id = ? AND rpi.scheduled_date = CURDATE()
        ORDER BY rpi.id ASC
        """,
        (rs, rowNum) -> new RehabDtos.RehabExercise(
            rs.getLong("plan_id"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getString("duration"),
            rs.getString("level"),
            rs.getInt("minutes"),
            jsonSupport.readStringList(rs.getString("steps_json")),
            rs.getString("caution"),
            rs.getString("focus"),
            jsonSupport.readStringList(rs.getString("benefits_json")),
            rs.getInt("video_minutes"),
            rs.getBoolean("done")
        ),
        userId
    );

    return new RehabDtos.RehabPlanResponse(
        "今日康复计划",
        exercises,
        buildWeekTrend(userId),
        getPlanSummaryInternal(userId),
        buildReminderSummary(userId)
    );
  }

  private void ensureTodayPlan(long userId) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM rehab_plan_items WHERE user_id = ? AND scheduled_date = CURDATE()",
        Integer.class,
        userId
    );
    if (count != null && count > 0) {
      return;
    }
    List<Long> exerciseIds = jdbcTemplate.query(
        """
        SELECT id
        FROM rehab_exercises
        WHERE user_id IS NULL OR user_id = ?
        ORDER BY CASE WHEN user_id = ? THEN 0 ELSE 1 END, id ASC
        LIMIT 4
        """,
        (rs, rowNum) -> rs.getLong("id"),
        userId,
        userId
    );
    if (exerciseIds.isEmpty()) {
      return;
    }
    replaceTodayPlanItems(userId, exerciseIds);
  }

  private RehabDtos.RehabPlanSummary getPlanSummaryInternal(long userId) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT focus, frequency, duration, intensity FROM rehab_plan_settings WHERE user_id = ? LIMIT 1",
        userId
    );
    if (!rows.isEmpty()) {
      Map<String, Object> row = rows.get(0);
      return normalizePlanSummary(
          stringValue(row.get("focus")),
          stringValue(row.get("frequency")),
          stringValue(row.get("duration")),
          stringValue(row.get("intensity"))
      );
    }

    List<RehabDtos.RehabExercise> exercises = jdbcTemplate.query(
        """
        SELECT re.name, re.category, re.duration, re.level, re.minutes, re.steps_json, re.caution, re.focus,
               re.benefits_json, re.video_minutes
        FROM rehab_plan_items rpi
        JOIN rehab_exercises re ON re.id = rpi.exercise_id
        WHERE rpi.user_id = ? AND rpi.scheduled_date = CURDATE()
        ORDER BY rpi.id ASC
        """,
        (rs, rowNum) -> new RehabDtos.RehabExercise(
            rs.getLong("video_minutes"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getString("duration"),
            rs.getString("level"),
            rs.getInt("minutes"),
            jsonSupport.readStringList(rs.getString("steps_json")),
            rs.getString("caution"),
            rs.getString("focus"),
            jsonSupport.readStringList(rs.getString("benefits_json")),
            rs.getInt("video_minutes"),
            false
        ),
        userId
    );
    if (exercises.isEmpty()) {
      return new RehabDtos.RehabPlanSummary("核心稳定", "每周 3 次", "单次 20 分钟", "中低强度");
    }

    int totalMinutes = exercises.stream().mapToInt(RehabDtos.RehabExercise::minutes).sum();
    boolean advanced = exercises.stream().anyMatch(item -> "进阶".equals(item.level()));
    String focus = exercises.stream()
        .map(RehabDtos.RehabExercise::focus)
        .filter(Objects::nonNull)
        .filter(value -> !value.isBlank())
        .findFirst()
        .orElse("核心稳定");

    return new RehabDtos.RehabPlanSummary(
        focus,
        "每周 3 次",
        totalMinutes > 0 ? "单次 " + totalMinutes + " 分钟" : "单次 20 分钟",
        advanced ? "中等强度" : "低到中等强度"
    );
  }

  private RehabDtos.RehabReminderSummary buildReminderSummary(long userId) {
    Map<String, Object> row = fetchPlanReminderRow(userId);
    if (row == null) {
      return new RehabDtos.RehabReminderSummary("--:--", "未设置", "未开启", "未设置");
    }
    List<String> dayLabels = normalizeReminderDays(jsonSupport.readStringList(String.valueOf(row.get("days_json")))).stream()
        .map(this::dayLabel)
        .toList();
    boolean enabled = boolValue(row.get("push_enabled"));
    return new RehabDtos.RehabReminderSummary(
        stringValue(row.get("reminder_time")),
        dayLabels.isEmpty() ? "未设置" : String.join(" / ", dayLabels),
        enabled ? "系统通知" : "未开启",
        enabled ? "已开启" : "已关闭"
    );
  }

  private RehabDtos.RehabWeekTrend buildWeekTrend(long userId) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        """
        SELECT stat_date, minutes
        FROM rehab_week_stats
        WHERE user_id = ? AND stat_date >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
        ORDER BY stat_date ASC
        """,
        userId
    );
    List<String> labels = new ArrayList<>();
    List<Integer> values = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      LocalDate date = row.get("stat_date") instanceof LocalDate localDate
          ? localDate
          : ((java.sql.Date) row.get("stat_date")).toLocalDate();
      labels.add(dayShortLabel(date.getDayOfWeek()));
      values.add(intValue(row.get("minutes"), 0));
    }
    int deltaPercent = 0;
    if (values.size() >= 2 && values.get(0) > 0) {
      deltaPercent = (int) Math.round(((values.get(values.size() - 1) - values.get(0)) * 100.0) / values.get(0));
    }
    String insight = values.isEmpty()
        ? "暂无训练趋势数据。"
        : (values.get(values.size() - 1) >= 25 ? "本周训练负荷保持稳定，可继续维持当前节奏。" : "本周训练时间偏少，建议提高执行一致性。");
    return new RehabDtos.RehabWeekTrend(labels, values, insight, deltaPercent);
  }

  private void validateDraft(RehabDtos.RehabPlanDraft request) {
    if (request == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "康复计划草案不能为空");
    }
    if (request.summary() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "康复计划摘要不能为空");
    }
    if (request.reminder() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "康复计划提醒不能为空");
    }
    if (request.exercises() == null || request.exercises().size() != 4) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "康复计划草案必须包含 4 个动作");
    }
  }

  private long resolveDraftExerciseId(long userId, RehabDtos.DraftExerciseCandidate candidate) {
    if (candidate == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "动作草案不能为空");
    }
    String name = stringValue(candidate.name()).trim();
    if (name.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "动作名称不能为空");
    }

    if ("existing".equalsIgnoreCase(stringValue(candidate.mode()))) {
      List<Long> ids = jdbcTemplate.query(
          """
          SELECT id
          FROM rehab_exercises
          WHERE name = ? AND (user_id IS NULL OR user_id = ?)
          ORDER BY CASE WHEN user_id = ? THEN 0 ELSE 1 END, id ASC
          LIMIT 1
          """,
          (rs, rowNum) -> rs.getLong("id"),
          name,
          userId,
          userId
      );
      if (!ids.isEmpty()) {
        return ids.get(0);
      }
    }

    List<Long> existingUserIds = jdbcTemplate.query(
        """
        SELECT id
        FROM rehab_exercises
        WHERE name = ? AND user_id = ?
        ORDER BY id ASC
        LIMIT 1
        """,
        (rs, rowNum) -> rs.getLong("id"),
        name,
        userId
    );
    if (!existingUserIds.isEmpty()) {
      return existingUserIds.get(0);
    }

    Long nextId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM rehab_exercises", Long.class);
    long exerciseId = nextId == null ? 1L : nextId;

    jdbcTemplate.update(
        """
        INSERT INTO rehab_exercises
          (id, user_id, name, category, duration, level, minutes, steps_json, caution, focus, benefits_json, video_minutes)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        exerciseId,
        userId,
        name,
        defaultIfBlank(candidate.category(), "康复训练"),
        defaultIfBlank(candidate.duration(), "单次 15 分钟"),
        normalizeLevel(candidate.level()),
        Math.max(candidate.minutes(), 1),
        jsonSupport.write(safeList(candidate.steps())),
        defaultIfBlank(candidate.caution(), "如出现明显不适，请暂停并咨询医生。"),
        defaultIfBlank(candidate.focus(), "恢复训练"),
        jsonSupport.write(safeList(candidate.benefits())),
        Math.max(candidate.videoMinutes(), 3)
    );
    return exerciseId;
  }

  private void replaceTodayPlanItems(long userId, List<Long> exerciseIds) {
    jdbcTemplate.update(
        "DELETE FROM rehab_plan_items WHERE user_id = ? AND scheduled_date = CURDATE()",
        userId
    );
    for (Long exerciseId : exerciseIds) {
      jdbcTemplate.update(
          """
          INSERT INTO rehab_plan_items (user_id, exercise_id, scheduled_date, done)
          VALUES (?, ?, CURDATE(), 0)
          """,
          userId,
          exerciseId
      );
    }
  }

  private void savePlanSummaryInternal(long userId, RehabDtos.RehabPlanSummary summary) {
    RehabDtos.RehabPlanSummary normalized = normalizePlanSummary(
        summary.focus(),
        summary.frequency(),
        summary.duration(),
        summary.intensity()
    );
    jdbcTemplate.update(
        """
        INSERT INTO rehab_plan_settings (user_id, focus, frequency, duration, intensity, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
          focus = VALUES(focus),
          frequency = VALUES(frequency),
          duration = VALUES(duration),
          intensity = VALUES(intensity),
          updated_at = NOW()
        """,
        userId,
        normalized.focus(),
        normalized.frequency(),
        normalized.duration(),
        normalized.intensity()
    );
  }

  private void savePlanReminderInternal(long userId, RehabDtos.PlanReminderDraft reminder) {
    jdbcTemplate.update(
        """
        INSERT INTO rehab_plan_reminders (user_id, reminder_time, days_json, push_enabled, created_at, updated_at)
        VALUES (?, ?, ?, ?, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
          reminder_time = VALUES(reminder_time),
          days_json = VALUES(days_json),
          push_enabled = VALUES(push_enabled),
          updated_at = NOW()
        """,
        userId,
        normalizeReminderTime(reminder.time()),
        jsonSupport.write(normalizeReminderDays(reminder.days())),
        reminder.pushEnabled() ? 1 : 0
    );
  }

  private Map<String, Object> fetchPlanReminderRow(long userId) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        """
        SELECT reminder_time, days_json, push_enabled
        FROM rehab_plan_reminders
        WHERE user_id = ?
        LIMIT 1
        """,
        userId
    );
    return rows.isEmpty() ? null : rows.get(0);
  }

  private RehabDtos.RehabPlanSummary normalizePlanSummary(String focus, String frequency, String duration, String intensity) {
    return new RehabDtos.RehabPlanSummary(
        defaultIfBlank(focus, "核心稳定"),
        defaultIfBlank(frequency, "每周 3 次"),
        defaultIfBlank(duration, "单次 20 分钟"),
        defaultIfBlank(intensity, "低到中等强度")
    );
  }

  private String normalizeReminderTime(String time) {
    String value = stringValue(time).trim();
    if (!value.matches("^\\d{2}:\\d{2}$")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "提醒时间无效");
    }
    return value;
  }

  private List<String> normalizeReminderDays(List<String> days) {
    List<String> normalized = new ArrayList<>();
    if (days != null) {
      for (String day : days) {
        String value = stringValue(day).trim().toLowerCase();
        if (VALID_REMINDER_DAYS.contains(value) && !normalized.contains(value)) {
          normalized.add(value);
        }
      }
    }
    if (normalized.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "请至少选择一个提醒日期");
    }
    return normalized;
  }

  private List<String> safeList(List<String> items) {
    if (items == null || items.isEmpty()) {
      return List.of("保持动作稳定完成");
    }
    return items.stream()
        .map(this::stringValue)
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .toList();
  }

  private String normalizeLevel(String level) {
    String value = stringValue(level).trim();
    if ("进阶".equals(value) || "advanced".equalsIgnoreCase(value) || "进阶级".equals(value)) {
      return "进阶";
    }
    return "基础";
  }

  private String defaultIfBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private boolean boolValue(Object value) {
    return value instanceof Number number && number.intValue() == 1;
  }

  private int intValue(Object value, int fallback) {
    return value instanceof Number number ? number.intValue() : fallback;
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private String dayLabel(String value) {
    return switch (value) {
      case "mon" -> "周一";
      case "tue" -> "周二";
      case "wed" -> "周三";
      case "thu" -> "周四";
      case "fri" -> "周五";
      case "sat" -> "周六";
      case "sun" -> "周日";
      default -> value;
    };
  }

  private String dayShortLabel(DayOfWeek dayOfWeek) {
    return switch (dayOfWeek) {
      case MONDAY -> "周一";
      case TUESDAY -> "周二";
      case WEDNESDAY -> "周三";
      case THURSDAY -> "周四";
      case FRIDAY -> "周五";
      case SATURDAY -> "周六";
      case SUNDAY -> "周日";
    };
  }
}
