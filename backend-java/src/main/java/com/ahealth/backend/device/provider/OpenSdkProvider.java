package com.ahealth.backend.device.provider;

import com.ahealth.backend.device.core.DeviceProvider;
import com.ahealth.backend.device.core.OAuthTokenExchange;
import com.ahealth.backend.device.core.UnifiedRecordWriter;
import com.ahealth.backend.device.model.UnifiedHealthRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * OpenSdk Provider（特殊 Provider，非 OAuth）。
 *
 * <p>开放给第三方接入的 SDK 入口：第三方通过 X-SDK-API-Key 鉴权后，
 * 调用 /api/devices/sdk/reading 推送数据，最终由本 Provider 写入 monitor_records。
 *
 * <p>不主动 pullData，仅通过 {@link #pushReading} 接收第三方推送。
 */
@Component
public class OpenSdkProvider implements DeviceProvider {

  private final UnifiedRecordWriter writer;

  public OpenSdkProvider(UnifiedRecordWriter writer) {
    this.writer = writer;
  }

  @Override public String providerName() { return "sdk"; }
  @Override public String displayName() { return "开放 SDK 接入"; }
  @Override public String deviceType() { return "other"; }
  @Override public boolean isConfigured() { return true; }
  @Override public boolean isAvailable() { return true; }

  @Override
  public List<String> supportedMetrics() {
    // SDK 接入支持所有 metric
    return List.of(
        "weight", "bmi", "heart_rate", "hrv", "steps", "calories",
        "blood_pressure", "blood_glucose", "sleep_duration", "sleep_stage",
        "spo2", "respiratory_rate", "body_temperature",
        "exercise_minutes", "stand_hours", "vo2_max",
        "rehab_motion", "rom"
    );
  }

  // ===== 非 OAuth 方法 =====
  @Override
  public String getAuthorizeUrl(long userId, String redirectUri) {
    throw new UnsupportedOperationException("OpenSdkProvider 不支持 OAuth");
  }

  @Override
  public OAuthTokenExchange exchangeCode(String code, String redirectUri) {
    throw new UnsupportedOperationException("OpenSdkProvider 不支持 OAuth");
  }

  @Override
  public OAuthTokenExchange refreshToken(String refreshToken) {
    throw new UnsupportedOperationException("OpenSdkProvider 不支持 OAuth");
  }

  @Override
  public List<UnifiedHealthRecord> pullData(
      long userId, String bindingExternalId, OAuthTokenExchange token, LocalDate from, LocalDate to
  ) {
    return List.of(); // 不主动 pull
  }

  // ===== 核心方法：接收第三方 SDK 推送 =====

  /**
   * 接收第三方 SDK 推送的 UnifiedHealthRecord 并写入 monitor_records。
   *
   * @param userId 用户 ID
   * @param sourceDevice 第三方设备标识
   * @param record 完整的 UnifiedHealthRecord（provider 字段会被强制覆盖为 "sdk"）
   * @return 写入后的 record
   */
  public UnifiedHealthRecord pushReading(long userId, String sourceDevice, UnifiedHealthRecord record) {
    if (record == null) {
      throw new IllegalArgumentException("record 不能为空");
    }
    // 强制 provider=sdk，避免第三方伪造其他 provider
    UnifiedHealthRecord sanitized = new UnifiedHealthRecord(
        "sdk",
        sourceDevice != null && !sourceDevice.isBlank() ? sourceDevice : "OpenSDK Device",
        record.recordedAt() != null ? record.recordedAt() : Instant.now(),
        record.heartRateAvgBpmInt(), record.heartRateRestingBpmInt(), record.hrvMillisInt(),
        record.stepsInt(), record.exerciseMinutesInt(), record.standHoursInt(),
        record.activeEnergyKcalInt(), record.flightsClimbedInt(), record.vo2MaxFloat(),
        record.stressScoreInt(),
        record.weightKgFloat(), record.heightCmFloat(), record.bmiFloat(),
        record.systolicBpInt(), record.diastolicBpInt(), record.bloodGlucoseFloat(),
        record.bodyTemperatureFloat(), record.spo2Int(), record.respiratoryRateInt(),
        record.sleepDurationHoursFloat(), record.deepSleepHoursFloat(), record.remSleepHoursFloat(),
        record.sleepScoreInt(), record.awakeTimesInt(), record.mindfulMinutesInt()
    );
    writer.writeRecord(userId, sanitized);
    return sanitized;
  }
}
