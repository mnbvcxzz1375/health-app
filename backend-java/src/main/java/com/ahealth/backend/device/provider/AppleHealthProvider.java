package com.ahealth.backend.device.provider;

import com.ahealth.backend.device.core.DeviceProvider;
import com.ahealth.backend.device.core.OAuthTokenExchange;
import com.ahealth.backend.device.core.UnifiedRecordWriter;
import com.ahealth.backend.device.model.DeviceAggregationDtos.AppleHealthSnapshotRequest;
import com.ahealth.backend.device.model.DeviceAggregationDtos.AppleHealthSnapshotRequest.BloodPressure;
import com.ahealth.backend.device.model.DeviceAggregationDtos.AppleHealthSnapshotRequest.BodyTemperature;
import com.ahealth.backend.device.model.DeviceAggregationDtos.AppleHealthSnapshotRequest.HeartRate;
import com.ahealth.backend.device.model.DeviceAggregationDtos.AppleHealthSnapshotRequest.OxygenSaturation;
import com.ahealth.backend.device.model.DeviceAggregationDtos.AppleHealthSnapshotRequest.RespiratoryRate;
import com.ahealth.backend.device.model.DeviceAggregationDtos.AppleHealthSnapshotRequest.SleepSession;
import com.ahealth.backend.device.model.UnifiedHealthRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Apple Health Provider（特殊 Provider，非 OAuth）。
 *
 * <p>不主动 pullData。前端通过 {@code appleHealthBridge.ts} 调用 HealthKit 桥接读取快照，
 * 然后通过 POST /api/devices/apple-health/snapshot 推送到后端。
 *
 * <p>快照字段映射到 {@link UnifiedHealthRecord} 的 9 个扩展列 + Physical/Sleep 字段。
 */
@Component
public class AppleHealthProvider implements DeviceProvider {

  private final UnifiedRecordWriter writer;

  public AppleHealthProvider(UnifiedRecordWriter writer) {
    this.writer = writer;
  }

  @Override
  public String providerName() { return "apple_health"; }

  @Override
  public String displayName() { return "Apple Health"; }

  @Override
  public String deviceType() { return "watch"; }

  @Override
  public boolean isConfigured() { return true; }

  @Override
  public boolean isAvailable() { return true; }

  @Override
  public List<String> supportedMetrics() {
    return List.of(
        "heart_rate", "hrv", "steps", "calories",
        "blood_pressure", "blood_glucose", "sleep_duration", "sleep_stage",
        "spo2", "respiratory_rate", "body_temperature",
        "exercise_minutes", "stand_hours", "vo2_max"
    );
  }

  // ===== 非 OAuth 方法：抛 UnsupportedOperationException =====
  @Override
  public String getAuthorizeUrl(long userId, String redirectUri) {
    throw new UnsupportedOperationException("AppleHealthProvider 不支持 OAuth");
  }

  @Override
  public OAuthTokenExchange exchangeCode(String code, String redirectUri) {
    throw new UnsupportedOperationException("AppleHealthProvider 不支持 OAuth");
  }

  @Override
  public OAuthTokenExchange refreshToken(String refreshToken) {
    throw new UnsupportedOperationException("AppleHealthProvider 不支持 OAuth");
  }

  @Override
  public List<UnifiedHealthRecord> pullData(
      long userId, String bindingExternalId, OAuthTokenExchange token, LocalDate from, LocalDate to
  ) {
    return List.of(); // 不主动 pull，仅接收 push
  }

  // ===== 核心方法：接收 Apple Health 快照 =====

  /**
   * 接收前端推送的 Apple Health 快照，转换为 {@link UnifiedHealthRecord} 写入 monitor_records。
   *
   * <p>快照中可能包含多时间点的数据（如心率、血压各有 measuredAt），统一以快照接收时间作为
   * recordedAt 写入（按小时去重），不同 metric 的覆盖通过 COALESCE 合并到同一条记录。
   */
  public void receiveSnapshot(long userId, AppleHealthSnapshotRequest snapshot) {
    if (snapshot == null) return;
    Instant now = Instant.now();
    UnifiedHealthRecord empty = UnifiedHealthRecord.empty("apple_health", "Apple Health", now);

    // 逐字段提取（null 字段保留 null，writer 会 COALESCE 保留原值）
    Integer hrAvg = null, hrResting = null;
    if (snapshot.heartRate() != null) {
      HeartRate hr = snapshot.heartRate();
      hrAvg = hr.avgBpm();
    }
    if (snapshot.restingHeartRate() != null) {
      hrResting = snapshot.restingHeartRate().bpm();
    }
    Integer hrv = snapshot.heartRateVariabilityMillis();
    Integer steps = snapshot.stepsToday();
    Integer exerciseMinutes = snapshot.exerciseMinutesToday();
    Integer standHours = snapshot.standHoursToday();
    Integer activeEnergy = snapshot.activeEnergyKcal();
    Integer flights = snapshot.flightsClimbedToday();
    Double vo2Max = snapshot.vo2Max();

    // Body Health
    Double weightKg = null, heightCm = null, bmi = null;
    Integer systolic = null, diastolic = null;
    if (snapshot.bloodPressure() != null) {
      BloodPressure bp = snapshot.bloodPressure();
      systolic = bp.systolicMmHg();
      diastolic = bp.diastolicMmHg();
    }
    Double bloodGlucose = null; // Apple Health 不直接提供血糖
    Double bodyTemp = null;
    if (snapshot.bodyTemperature() != null) {
      bodyTemp = snapshot.bodyTemperature().celsius();
    }
    Integer spo2 = null;
    if (snapshot.oxygenSaturation() != null) {
      Double pct = snapshot.oxygenSaturation().percentage();
      spo2 = pct == null ? null : (int) Math.round(pct);
    }
    Integer respiratoryRate = null;
    if (snapshot.respiratoryRate() != null) {
      respiratoryRate = snapshot.respiratoryRate().ratePerMinute();
    }

    // Sleep
    Double sleepDurationHours = null, deepHours = null, remHours = null;
    Integer awakeTimes = null, sleepScore = null, mindfulMinutes = null;
    if (snapshot.sleepSession() != null) {
      SleepSession sleep = snapshot.sleepSession();
      if (sleep.totalMinutes() != null) sleepDurationHours = sleep.totalMinutes() / 60.0;
      if (sleep.deepSleepMinutes() != null) deepHours = sleep.deepSleepMinutes() / 60.0;
      if (sleep.remSleepMinutes() != null) remHours = sleep.remSleepMinutes() / 60.0;
      if (sleep.awakeMinutes() != null && sleep.awakeMinutes() > 0) awakeTimes = 1;
    }
    if (snapshot.mindfulMinutesToday() != null) {
      mindfulMinutes = snapshot.mindfulMinutesToday();
    }

    UnifiedHealthRecord record = new UnifiedHealthRecord(
        empty.provider(), empty.sourceDevice(), now,
        hrAvg, hrResting, hrv, steps, exerciseMinutes, standHours, activeEnergy, flights, vo2Max, null,
        weightKg, heightCm, bmi, systolic, diastolic, bloodGlucose, bodyTemp, spo2, respiratoryRate,
        sleepDurationHours, deepHours, remHours, sleepScore, awakeTimes, mindfulMinutes
    );
    writer.writeRecord(userId, record);
  }
}
