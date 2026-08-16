package com.ahealth.backend.device.model;

import java.time.LocalDateTime;

/**
 * 设备同步日志。
 * 对应 device_sync_logs 表，每次同步（手动或自动）记一条。
 */
public record DeviceSyncLog(
    int id,
    int bindingId,
    long userId,
    LocalDateTime syncStartedAt,
    LocalDateTime syncEndedAt,
    String status,                      // success/failed/partial
    int recordsPulled,                  // 从厂商拉取的记录数
    int recordsWritten,                 // 实际写入 monitor_records 的记录数
    String errorMessage,
    String detailsJson,
    LocalDateTime createdAt
) {}
