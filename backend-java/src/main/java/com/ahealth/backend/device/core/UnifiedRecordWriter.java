package com.ahealth.backend.device.core;

import com.ahealth.backend.device.model.UnifiedHealthRecord;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 统一健康数据写入层。
 *
 * <p>所有 Provider（OAuth 厂商 / Apple Health / Manual / Bluetooth / SDK）的数据
 * 最终通过此服务 upsert 到 {@code monitor_records} 表。
 *
 * <p>upsert 策略（沿用 {@code RookService.syncToMonitorRecords()} 既有模式）：
 * 按 {@code DATE(recorded_at) + HOUR(recorded_at)} 去重。命中则 UPDATE，否则 INSERT。
 *
 * <p>字段映射覆盖 {@link UnifiedHealthRecord} 全部 25 个字段，未提供的字段保留 NULL（或 0，对 NOT NULL 列）。
 */
@Service
public class UnifiedRecordWriter {

  private final JdbcTemplate jdbc;

  public UnifiedRecordWriter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 写入单条记录，返回是否走了 INSERT 路径（false=UPDATE）。 */
  public boolean writeRecord(long userId, UnifiedHealthRecord r) {
    if (r == null) return false;
    LocalDateTime recordedAt = toLocalDateTime(r.recordedAt());
    if (recordedAt == null) {
      recordedAt = LocalDateTime.now();
    }

    Integer existing = jdbc.queryForObject(
        "SELECT COUNT(*) FROM monitor_records "
            + "WHERE user_id = ? AND DATE(recorded_at) = DATE(?) AND HOUR(recorded_at) = HOUR(?)",
        Integer.class, userId, recordedAt, recordedAt);

    if (existing != null && existing > 0) {
      updateRecord(userId, r, recordedAt);
      return false;
    }
    insertRecord(userId, r, recordedAt);
    return true;
  }

  /** 批量写入，返回实际新增数。 */
  public int writeRecords(long userId, List<UnifiedHealthRecord> records) {
    if (records == null || records.isEmpty()) return 0;
    int inserted = 0;
    for (UnifiedHealthRecord r : records) {
      if (writeRecord(userId, r)) inserted++;
    }
    return inserted;
  }

  // ===== 内部：INSERT / UPDATE =====

  private void insertRecord(long userId, UnifiedHealthRecord r, LocalDateTime recordedAt) {
    // hr 列在 V1 schema 中为 NOT NULL，所有 NOT NULL 列均需给默认值（0）
    // 其余 nullable 列直接传 null
    jdbc.update(
        """
        INSERT INTO monitor_records (
          user_id, recorded_at, hr, sleep_score, deep_sleep_hours, awake_times,
          stress_score, systolic_bp, diastolic_bp, vo2_max, exercise_minutes,
          stand_hours, active_energy_kcal, flights_climbed, hrv_millis,
          mindful_minutes, walking_hr_avg, steps,
          blood_glucose, body_temperature, spo2, respiratory_rate,
          weight_kg, height_cm, bmi, sleep_rem_hours
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        userId,
        Timestamp.valueOf(recordedAt),
        nvl(r.heartRateAvgBpmInt(), 0),
        r.sleepScoreInt(),
        r.deepSleepHoursFloat(),
        r.awakeTimesInt(),
        r.stressScoreInt(),
        r.systolicBpInt(),
        r.diastolicBpInt(),
        r.vo2MaxFloat(),
        r.exerciseMinutesInt(),
        r.standHoursInt(),
        r.activeEnergyKcalInt(),
        r.flightsClimbedInt(),
        r.hrvMillisInt(),
        r.mindfulMinutesInt(),
        null, // walking_hr_avg 暂无对应字段（保留供 Apple Health 扩展）
        r.stepsInt(),
        r.bloodGlucoseFloat(),
        r.bodyTemperatureFloat(),
        r.spo2Int(),
        r.respiratoryRateInt(),
        r.weightKgFloat(),
        r.heightCmFloat(),
        r.bmiFloat(),
        r.remSleepHoursFloat()
    );
  }

  private void updateRecord(long userId, UnifiedHealthRecord r, LocalDateTime recordedAt) {
    // 仅在原值非空或新值非空时更新（COALESCE 保留原值），避免覆盖更早的 finer-grained 数据
    jdbc.update(
        """
        UPDATE monitor_records SET
          hr              = COALESCE(?, hr),
          sleep_score     = COALESCE(?, sleep_score),
          deep_sleep_hours= COALESCE(?, deep_sleep_hours),
          awake_times     = COALESCE(?, awake_times),
          stress_score    = COALESCE(?, stress_score),
          systolic_bp     = COALESCE(?, systolic_bp),
          diastolic_bp    = COALESCE(?, diastolic_bp),
          vo2_max         = COALESCE(?, vo2_max),
          exercise_minutes= COALESCE(?, exercise_minutes),
          stand_hours     = COALESCE(?, stand_hours),
          active_energy_kcal = COALESCE(?, active_energy_kcal),
          flights_climbed = COALESCE(?, flights_climbed),
          hrv_millis      = COALESCE(?, hrv_millis),
          mindful_minutes = COALESCE(?, mindful_minutes),
          steps           = COALESCE(?, steps),
          blood_glucose   = COALESCE(?, blood_glucose),
          body_temperature= COALESCE(?, body_temperature),
          spo2            = COALESCE(?, spo2),
          respiratory_rate= COALESCE(?, respiratory_rate),
          weight_kg       = COALESCE(?, weight_kg),
          height_cm       = COALESCE(?, height_cm),
          bmi             = COALESCE(?, bmi),
          sleep_rem_hours = COALESCE(?, sleep_rem_hours)
        WHERE user_id = ? AND DATE(recorded_at) = DATE(?) AND HOUR(recorded_at) = HOUR(?)
        """,
        r.heartRateAvgBpmInt(),
        r.sleepScoreInt(),
        r.deepSleepHoursFloat(),
        r.awakeTimesInt(),
        r.stressScoreInt(),
        r.systolicBpInt(),
        r.diastolicBpInt(),
        r.vo2MaxFloat(),
        r.exerciseMinutesInt(),
        r.standHoursInt(),
        r.activeEnergyKcalInt(),
        r.flightsClimbedInt(),
        r.hrvMillisInt(),
        r.mindfulMinutesInt(),
        r.stepsInt(),
        r.bloodGlucoseFloat(),
        r.bodyTemperatureFloat(),
        r.spo2Int(),
        r.respiratoryRateInt(),
        r.weightKgFloat(),
        r.heightCmFloat(),
        r.bmiFloat(),
        r.remSleepHoursFloat(),
        userId, recordedAt, recordedAt
    );
  }

  // ===== 工具方法 =====

  private static LocalDateTime toLocalDateTime(Instant instant) {
    if (instant == null) return null;
    return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
  }

  private static Integer nvl(Integer v, int def) {
    return v == null ? def : v;
  }
}
