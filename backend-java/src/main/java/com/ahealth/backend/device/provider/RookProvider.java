package com.ahealth.backend.device.provider;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.device.RookDtos;
import com.ahealth.backend.device.RookService;
import com.ahealth.backend.device.core.DeviceProvider;
import com.ahealth.backend.device.core.OAuthTokenExchange;
import com.ahealth.backend.device.model.UnifiedHealthRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * ROOK Unified Device API Provider（DeviceProvider 接口实现）。
 *
 * <p>ROOK 是聚合 14+ 厂商的中间层，对外提供统一 API（Basic Auth 鉴权）。
 * ROOK 不是标准 OAuth 流程：用户授权需走 ROOK 的 data_source/authorizer 端点，
 * 后端通过 ROOK_CLIENT_UUID/ROOK_CLIENT_SECRET 鉴权后直接拉取数据。
 *
 * <p>本类把 RookService 包装为 DeviceProvider，使设备聚合平台可以统一调度。
 * 未配置凭证时返回空数据，不影响其他 Provider。
 */
@Component
public class RookProvider implements DeviceProvider {

  private final RookService rookService;

  public RookProvider(RookService rookService) {
    this.rookService = rookService;
  }

  @Override public String providerName() { return "rook"; }
  @Override public String displayName() { return "ROOK (Unified)"; }
  @Override public String deviceType() { return "other"; }

  @Override
  public boolean isConfigured() {
    return rookService.isConfigured();
  }

  @Override
  public boolean isAvailable() {
    // ROOK 作为可选 fallback：即使已配置，也只在用户主动选择时才使用
    return isConfigured();
  }

  // ===== OAuth 方法：ROOK 不是标准 OAuth，抛 UnsupportedOperationException =====
  @Override
  public String getAuthorizeUrl(long userId, String redirectUri) {
    // ROOK 提供 data_source/authorizer 端点，但鉴权与标准 OAuth 不同
    // 用户通过 RookController 单独发起 ROOK 授权，本 Provider 仅用于数据拉取
    throw new UnsupportedOperationException(
        "RookProvider 不支持标准 OAuth 流程，请通过 /api/rook/data-source/{name}/authorizer 发起授权");
  }

  @Override
  public OAuthTokenExchange exchangeCode(String code, String redirectUri) {
    throw new UnsupportedOperationException("RookProvider 不支持标准 OAuth");
  }

  @Override
  public OAuthTokenExchange refreshToken(String refreshToken) {
    throw new UnsupportedOperationException("RookProvider 不支持标准 OAuth");
  }

  @Override
  public List<UnifiedHealthRecord> pullData(
      long userId, String bindingExternalId, OAuthTokenExchange token, LocalDate from, LocalDate to
  ) {
    if (!isConfigured()) {
      return List.of();
    }
    try {
      // 通过 RookService 拉取 from 到 to 范围的数据（按日循环）
      java.util.List<UnifiedHealthRecord> records = new java.util.ArrayList<>();
      String rookUserId = (bindingExternalId == null || bindingExternalId.isBlank())
          ? String.valueOf(userId) : bindingExternalId;

      LocalDate cursor = from;
      while (!cursor.isAfter(to)) {
        String dateStr = cursor.toString();
        try {
          RookDtos.PhysicalHealthSummary physical = rookService.getPhysicalHealthSummary(rookUserId, dateStr);
          RookDtos.SleepHealthSummary sleep = rookService.getSleepHealthSummary(rookUserId, dateStr);
          records.addAll(convertRookToUnified(physical, sleep, cursor));
        } catch (Exception ignored) {
          // 单日失败不影响其他日
        }
        cursor = cursor.plusDays(1);
      }
      return records;
    } catch (Exception e) {
      return List.of();
    }
  }

  @Override
  public List<String> supportedMetrics() {
    return List.of(
        "heart_rate", "hrv", "steps", "calories", "stress_score", "vo2_max",
        "sleep_duration", "deep_sleep", "rem_sleep", "spo2", "respiratory_rate"
    );
  }

  // ===== 数据转换：RookDtos → UnifiedHealthRecord =====
  private List<UnifiedHealthRecord> convertRookToUnified(
      RookDtos.PhysicalHealthSummary physical, RookDtos.SleepHealthSummary sleep, LocalDate date
  ) {
    List<UnifiedHealthRecord> records = new ArrayList<>();
    Instant recordedAt = date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);

    // Physical 记录（步数 / 心率 / 卡路里 / 压力 / 爬楼 / SpO2 / VO2Max）
    if (physical != null) {
      UnifiedHealthRecord empty = UnifiedHealthRecord.empty("rook", "ROOK Unified", recordedAt);
      Integer hrAvg = toInt(physical.heartRate().avgBpm());
      Integer hrRest = toInt(physical.heartRate().restingBpm());
      Integer hrv = toInt(physical.heartRate().hrvAvgRmssd());
      Integer steps = physical.distance().steps();
      Integer floors = physical.distance().floorsClimbed();
      Integer exerciseMin = (int) (physical.activity().activeSeconds() / 60);
      Integer activeKcal = toInt(physical.calories().netActiveKcal());
      Integer stressScore = toInt(physical.stress().avgLevel() * 10);
      Integer spo2 = toInt(physical.oxygenation().avgSpo2());
      Double vo2Max = physical.oxygenation().vo2Max();
      records.add(new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), recordedAt,
          hrAvg, hrRest, hrv, steps, exerciseMin, null, activeKcal, floors, vo2Max, stressScore,
          null, null, null, null, null, null, null,
          spo2, null,
          null, null, null, null, null, null));
    }

    // Sleep 记录（睡眠时长 / 深睡 / REM / 评分 / 夜醒）
    if (sleep != null && sleep.duration() != null) {
      UnifiedHealthRecord empty = UnifiedHealthRecord.empty("rook", "ROOK Unified", recordedAt);
      Double totalH = sleep.duration().totalSleepSeconds() / 3600.0;
      Double deepH = sleep.duration().deepSleepSeconds() / 3600.0;
      Double remH = sleep.duration().remSleepSeconds() / 3600.0;
      Integer sleepScore = sleep.scores() != null ? sleep.scores().qualityRating() * 20 : null;
      Integer awakeCount = sleep.duration().timeAwakeDuringSleepSeconds() > 0 ? 1 : 0;
      Integer rr = sleep.breathing() != null ? toInt(sleep.breathing().breathsAvgPerMin()) : null;
      records.add(new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), recordedAt,
          null, null, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null, null,
          rr,
          totalH, deepH, remH, sleepScore, awakeCount, null));
    }

    return records;
  }

  private static Integer toInt(double value) {
    return value > 0 ? (int) Math.round(value) : null;
  }
}
