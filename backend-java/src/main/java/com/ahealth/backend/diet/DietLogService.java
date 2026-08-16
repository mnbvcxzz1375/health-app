package com.ahealth.backend.diet;

import com.ahealth.backend.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户饮食记录及其可追溯更正/删除审计。 */
@Service
public class DietLogService {
  private static final String LOG_COLUMNS =
      "id, food_name, category, weight_grams, calories, protein_g, carbs_g, fat_g, source, recorded_at";
  private static final RowMapper<DietDtos.DietLogEntry> LOG_MAPPER = (rs, rowNum) ->
      new DietDtos.DietLogEntry(
          rs.getLong("id"), rs.getString("food_name"), rs.getString("category"),
          rs.getDouble("weight_grams"), rs.getDouble("calories"), rs.getDouble("protein_g"),
          rs.getDouble("carbs_g"), rs.getDouble("fat_g"), rs.getString("source"),
          rs.getTimestamp("recorded_at").toLocalDateTime()
      );

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public DietLogService(JdbcTemplate jdbcTemplate) {
    this(jdbcTemplate, new ObjectMapper().findAndRegisterModules());
  }

  @Autowired
  public DietLogService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public DietDtos.DietLogEntry save(long userId, DietDtos.DietLogSaveRequest request) {
    validate(request);
    LocalDateTime recordedAt = request.recordedAt() == null ? LocalDateTime.now() : request.recordedAt();
    jdbcTemplate.update(
        """
        INSERT INTO diet_logs (
          user_id, food_name, category, weight_grams, calories, protein_g, carbs_g, fat_g, source, recorded_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        userId, request.foodName().trim(), value(request.category()), request.weightGrams(),
        request.calories(), request.protein(), request.carbs(), request.fat(), value(request.source()),
        Timestamp.valueOf(recordedAt)
    );
    DietDtos.DietLogEntry entry = latest(userId);
    recordAudit(userId, entry.id(), "created", null, snapshot(entry), "用户新增饮食记录");
    return entry;
  }

  public List<DietDtos.DietLogEntry> listToday(long userId) {
    return jdbcTemplate.query(
        "SELECT " + LOG_COLUMNS + " FROM diet_logs "
            + "WHERE user_id = ? AND recorded_at >= ? "
            + "ORDER BY recorded_at DESC, id DESC",
        LOG_MAPPER,
        userId, Timestamp.valueOf(LocalDate.now().atStartOfDay())
    );
  }

  @Transactional
  public DietDtos.DietLogEntry update(
      long userId,
      long dietLogId,
      DietDtos.DietLogSaveRequest request
  ) {
    validate(request);
    DietDtos.DietLogEntry before = findById(userId, dietLogId);
    LocalDateTime recordedAt = request.recordedAt() == null ? before.recordedAt() : request.recordedAt();
    int affected = jdbcTemplate.update(
        """
        UPDATE diet_logs SET food_name=?, category=?, weight_grams=?, calories=?, protein_g=?,
          carbs_g=?, fat_g=?, source=?, recorded_at=? WHERE id=? AND user_id=?
        """,
        request.foodName().trim(), value(request.category()), request.weightGrams(), request.calories(),
        request.protein(), request.carbs(), request.fat(), value(request.source()), Timestamp.valueOf(recordedAt),
        dietLogId, userId
    );
    if (affected != 1) {
      throw new ApiException(HttpStatus.NOT_FOUND, "饮食记录不存在或无权修改");
    }
    DietDtos.DietLogEntry after = findById(userId, dietLogId);
    recordAudit(userId, dietLogId, "updated", snapshot(before), snapshot(after), "用户更正饮食记录");
    return after;
  }

  @Transactional
  public DietDtos.DietLogOperationResult delete(long userId, long dietLogId) {
    DietDtos.DietLogEntry before = findById(userId, dietLogId);
    int affected = jdbcTemplate.update(
        "DELETE FROM diet_logs WHERE id=? AND user_id=?", dietLogId, userId);
    if (affected != 1) {
      throw new ApiException(HttpStatus.NOT_FOUND, "饮食记录不存在或无权删除");
    }
    recordAudit(userId, dietLogId, "deleted", snapshot(before), null, "用户删除饮食记录");
    return new DietDtos.DietLogOperationResult(true, dietLogId, "饮食记录已删除");
  }

  public List<DietDtos.DietLogAuditEntry> listAudit(long userId, long dietLogId) {
    return jdbcTemplate.query(
        """
        SELECT id, diet_log_id, action, before_json, after_json, reason, created_at
        FROM diet_log_audits WHERE user_id=? AND diet_log_id=? ORDER BY id DESC
        """,
        (rs, rowNum) -> new DietDtos.DietLogAuditEntry(
            rs.getLong("id"), rs.getLong("diet_log_id"), rs.getString("action"),
            rs.getString("before_json"), rs.getString("after_json"), rs.getString("reason"),
            rs.getTimestamp("created_at").toLocalDateTime()
        ),
        userId, dietLogId
    );
  }

  private DietDtos.DietLogEntry findById(long userId, long dietLogId) {
    List<DietDtos.DietLogEntry> rows = jdbcTemplate.query(
        "SELECT " + LOG_COLUMNS + " FROM diet_logs WHERE id=? AND user_id=?",
        LOG_MAPPER, dietLogId, userId);
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "饮食记录不存在或无权访问");
    }
    return rows.get(0);
  }

  private DietDtos.DietLogEntry latest(long userId) {
    List<DietDtos.DietLogEntry> rows = jdbcTemplate.query(
        "SELECT " + LOG_COLUMNS + " FROM diet_logs WHERE user_id = ? ORDER BY id DESC LIMIT 1",
        LOG_MAPPER, userId);
    if (rows.isEmpty()) throw new IllegalStateException("饮食日志写入后未找到记录");
    return rows.get(0);
  }

  private void validate(DietDtos.DietLogSaveRequest request) {
    if (request == null || request.foodName() == null || request.foodName().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "食物名称不能为空");
    }
    if (!isValidNonNegative(request.weightGrams()) || !isValidNonNegative(request.calories())
        || !isValidNonNegative(request.protein()) || !isValidNonNegative(request.carbs())
        || !isValidNonNegative(request.fat())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "饮食营养值必须是有限的非负数");
    }
  }

  private boolean isValidNonNegative(double value) {
    return Double.isFinite(value) && value >= 0;
  }

  private void recordAudit(
      long userId,
      long dietLogId,
      String action,
      String beforeJson,
      String afterJson,
      String reason
  ) {
    jdbcTemplate.update(
        """
        INSERT INTO diet_log_audits (diet_log_id, user_id, action, before_json, after_json, reason, created_at)
        VALUES (?, ?, ?, ?, ?, ?, NOW())
        """,
        dietLogId, userId, action, beforeJson, afterJson, reason
    );
  }

  private String snapshot(DietDtos.DietLogEntry entry) {
    try {
      return objectMapper.writeValueAsString(entry);
    } catch (Exception ex) {
      throw new IllegalStateException("饮食日志审计快照生成失败", ex);
    }
  }

  private String value(String value) {
    return value == null ? "" : value.trim();
  }
}
