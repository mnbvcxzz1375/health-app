package com.ahealth.backend.device.model;

import java.time.LocalDateTime;

/**
 * 设备绑定记录（用户 × Provider）。
 * 对应 device_bindings 表，token 字段为已解密的明文（仅在内存中传递，落库前由 TokenEncryptionService 加密）。
 */
public record DeviceBinding(
    int id,
    long userId,
    String provider,                    // garmin/oura/.../apple_health/bluetooth/manual/sdk
    String externalUserId,              // 厂商侧用户 ID
    String displayName,
    String deviceType,                  // watch/scale/bp_monitor/cgm/sleep_monitor/pulse_ox/thermometer/rehab_sensor/ring/other
    String status,                      // connected/stale/disconnected
    String accessToken,                 // 已解密明文，仅内存中
    String refreshToken,                // 已解密明文，仅内存中
    LocalDateTime tokenExpiresAt,
    LocalDateTime lastSyncAt,
    String lastSyncStatus,              // success/failed/partial
    String lastError,
    String metadataJson,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
