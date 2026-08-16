package com.ahealth.backend.device.provider;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.device.core.DeviceProvider;
import com.ahealth.backend.device.core.OAuthTokenExchange;
import com.ahealth.backend.device.core.UnifiedRecordWriter;
import com.ahealth.backend.device.model.UnifiedHealthRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 手动输入 Provider（特殊 Provider，非 OAuth）。
 *
 * <p>不主动 pullData，仅通过 {@link #pushData(long, String, Double, Instant)} 接收前端手动输入。
 * 永远 isConfigured()=true，作为所有 metric 的最终 fallback。
 */
@Component
public class ManualInputProvider implements DeviceProvider {

  private final UnifiedRecordWriter writer;

  public ManualInputProvider(UnifiedRecordWriter writer) {
    this.writer = writer;
  }

  @Override
  public String providerName() { return "manual"; }

  @Override
  public String displayName() { return "手动输入"; }

  @Override
  public String deviceType() { return "other"; }

  @Override
  public boolean isConfigured() { return true; }

  @Override
  public boolean isAvailable() { return true; }

  @Override
  public List<String> supportedMetrics() {
    return List.of(
        "weight", "bmi", "heart_rate", "hrv", "steps", "calories",
        "blood_pressure", "blood_glucose", "sleep_duration", "sleep_stage",
        "spo2", "respiratory_rate", "body_temperature",
        "exercise_minutes", "stand_hours", "vo2_max",
        "rehab_motion", "rom"
    );
  }

  // ===== 非 OAuth 方法：抛 UnsupportedOperationException =====
  @Override
  public String getAuthorizeUrl(long userId, String redirectUri) {
    throw new UnsupportedOperationException("ManualInputProvider 不支持 OAuth");
  }

  @Override
  public OAuthTokenExchange exchangeCode(String code, String redirectUri) {
    throw new UnsupportedOperationException("ManualInputProvider 不支持 OAuth");
  }

  @Override
  public OAuthTokenExchange refreshToken(String refreshToken) {
    throw new UnsupportedOperationException("ManualInputProvider 不支持 OAuth");
  }

  @Override
  public List<UnifiedHealthRecord> pullData(
      long userId, String bindingExternalId, OAuthTokenExchange token, LocalDate from, LocalDate to
  ) {
    return List.of(); // 不主动 pull
  }

  // ===== 核心方法：接收前端手动输入 =====

  /**
   * 接收前端手动输入，转 {@link UnifiedHealthRecord} 后写入 monitor_records。
   *
   * @param userId    用户 ID
   * @param metric    metric 名称（weight/heart_rate/blood_pressure/...）
   * @param value     数值（血压以 systolic*1000+diastolic 编码，前端约定）
   * @param recordedAt 采集时间，null 则用当前时间
   * @return 写入后的 record（用于回执）
   */
  public UnifiedHealthRecord pushData(long userId, String metric, Double value, Instant recordedAt) {
    if (metric == null || metric.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "metric 不能为空");
    }
    if (value == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "value 不能为空");
    }
    Instant at = recordedAt != null ? recordedAt : Instant.now();

    UnifiedHealthRecord record = mapMetricToRecord(metric, value, at);
    writer.writeRecord(userId, record);
    return record;
  }

  /** 把单个 metric/value 映射到 UnifiedHealthRecord 的对应字段。 */
  private UnifiedHealthRecord mapMetricToRecord(String metric, double value, Instant at) {
    UnifiedHealthRecord empty = UnifiedHealthRecord.empty("manual", "Manual Input", at);
    return switch (metric) {
      case "weight" -> new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), at,
          null, null, null, null, null, null, null, null, null,
          null, value, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null);
      case "bmi" -> new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), at,
          null, null, null, null, null, null, null, null, null,
          null, null, null, value, null, null, null, null, null,
          null, null, null, null, null, null, null);
      case "heart_rate" -> new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), at,
          (int) value, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null);
      case "hrv" -> new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), at,
          null, null, (int) value, null, null, null, null, null, null,
          null, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null);
      case "steps" -> new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), at,
          null, null, null, (int) value, null, null, null, null, null,
          null, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null);
      case "calories" -> new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), at,
          null, null, null, null, null, null, (int) value, null, null,
          null, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null);
      case "blood_pressure" -> {
        // 约定：value = systolic * 1000 + diastolic（如 120*1000+80 = 120080）
        int systolic = (int) (value / 1000);
        int diastolic = (int) (value % 1000);
        yield new UnifiedHealthRecord(
            empty.provider(), empty.sourceDevice(), at,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, systolic, diastolic, null, null, null,
            null, null, null, null, null, null, null);
      }
      case "blood_glucose" -> new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), at,
          null, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, value, null, null,
          null, null, null, null, null, null, null);
      case "sleep_duration" -> new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), at,
          null, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null, null, null, null,
          value, null, null, null, null, null);
      case "spo2" -> new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), at,
          null, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null, null, (int) value,
          null, null, null, null, null, null, null);
      case "respiratory_rate" -> new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), at,
          null, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null, null, null, (int) value,
          null, null, null, null, null, null);
      case "body_temperature" -> new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), at,
          null, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null, value, null,
          null, null, null, null, null, null, null);
      case "exercise_minutes" -> new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), at,
          null, null, null, null, (int) value, null, null, null, null,
          null, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null);
      case "stand_hours" -> new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), at,
          null, null, null, null, null, (int) value, null, null, null,
          null, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null);
      case "vo2_max" -> new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), at,
          null, null, null, null, null, null, null, null, value,
          null, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null);
      default -> throw new ApiException(
          HttpStatus.BAD_REQUEST, "metric [" + metric + "] 不支持手动输入");
    };
  }
}
