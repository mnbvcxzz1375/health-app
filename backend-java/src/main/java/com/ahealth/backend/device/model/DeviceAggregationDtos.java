package com.ahealth.backend.device.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备聚合平台 Controller 用 DTO 集合。
 * 集中放置避免文件碎片化。
 */
public final class DeviceAggregationDtos {
  private DeviceAggregationDtos() {}

  /** GET /api/devices/providers 返回的 Provider 信息 */
  public record ProviderInfo(
      String providerName,           // garmin/oura/manual/...
      String displayName,            // Garmin/Oura Ring/手动输入/...
      String deviceType,             // watch/scale/...
      boolean configured,            // 是否配置了 client_id/secret
      boolean available,             // 是否可用（configured && 非 OAuth 类型总是 true）
      List<String> supportedMetrics  // ["heart_rate","steps",...]
  ) {}

  /** GET /api/devices/bindings 返回的绑定列表项 */
  public record BindingItem(
      int id,
      String provider,
      String displayName,
      String deviceType,
      String status,
      LocalDateTime lastSyncAt,
      String lastSyncStatus,
      String lastError
  ) {}

  /** POST /api/devices/bindings/{provider}/authorize 启动 OAuth 返回 */
  public record AuthorizeResponse(
      String provider,
      String authorizationUrl
  ) {}

  /** GET /api/devices/route/{metric} 自动路由返回 */
  public record MetricRouteResponse(
      String metric,
      String metricLabel,
      String preferredDeviceType,
      String fallbackDeviceType,
      String pillar,
      String icon,
      List<SourceItem> connectedSources,    // 已连接（status=connected）
      List<SourceItem> staleSources,        // 之前连过但超 24h 未同步
      List<SourceItem> availableSources,    // 已配置但未绑定
      boolean manualInputSupported
  ) {}

  /** 路由返回中的设备源项 */
  public record SourceItem(
      String provider,
      String displayName,
      String deviceType,
      String status,                        // connected/stale/disconnected/available
      LocalDateTime lastSyncAt,
      String bindingDisplayName             // 已绑定时为用户的别名，否则 null
  ) {}

  /** POST /api/devices/manual 手动输入请求 */
  public record ManualInputRequest(
      String metric,                        // weight/heart_rate/blood_pressure/...
      Double value,                         // 数值
      String recordedAt,                    // ISO 时间，可空（默认 now）
      String note                           // 备注，可空
  ) {}

  /** POST /api/devices/manual 手动输入返回 */
  public record ManualInputResponse(
      boolean accepted,
      String metric,
      double value,
      String recordedAt
  ) {}

  /** POST /api/devices/apple-health/snapshot 请求体（与前端 AppleHealthSnapshot 对齐） */
  public record AppleHealthSnapshotRequest(
      String source,
      HeartRate heartRate,
      RestingHeartRate restingHeartRate,
      Integer heartRateVariabilityMillis,
      Integer walkingHeartRateAvg,
      BloodPressure bloodPressure,
      OxygenSaturation oxygenSaturation,
      SleepSession sleepSession,
      Integer stepsToday,
      Integer standMinutesToday,
      Integer standHoursToday,
      Integer exerciseMinutesToday,
      Integer activeEnergyKcal,
      Integer restingEnergyKcal,
      Double vo2Max,
      Integer flightsClimbedToday,
      Double distanceWalkingRunningMeters,
      BodyTemperature bodyTemperature,
      RespiratoryRate respiratoryRate,
      Integer mindfulMinutesToday
  ) {
    public record HeartRate(Integer avgBpm, Integer minBpm, Integer maxBpm, String measuredAt) {}
    public record RestingHeartRate(Integer bpm, String measuredAt) {}
    public record BloodPressure(Integer systolicMmHg, Integer diastolicMmHg, String measuredAt) {}
    public record OxygenSaturation(Double percentage, String measuredAt) {}
    public record SleepSession(
        String startAt, String endAt, Integer totalMinutes,
        Integer deepSleepMinutes, Integer remSleepMinutes, Integer awakeMinutes
    ) {}
    public record BodyTemperature(Double celsius, String measuredAt) {}
    public record RespiratoryRate(Integer ratePerMinute, String measuredAt) {}
  }

  /** POST /api/devices/apple-health/snapshot 返回 */
  public record AppleHealthSnapshotResponse(
      boolean accepted,
      String recordedAt,
      String message
  ) {}

  /** GET /api/devices/sync-logs/{bindingId} 返回 */
  public record SyncLogItem(
      int id,
      int bindingId,
      LocalDateTime syncStartedAt,
      LocalDateTime syncEndedAt,
      String status,
      int recordsPulled,
      int recordsWritten,
      String errorMessage
  ) {}

  /** 通用操作结果 */
  public record OperationResult(boolean success, String message) {}
}
