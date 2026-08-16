package com.ahealth.backend.device.model;

/**
 * metric → device_type 路由配置。
 * 对应 device_metric_routes 表，由 BackendSchemaInitializer 启动时插入 18 条种子数据。
 */
public record DeviceMetricRoute(
    int id,
    String metric,                      // weight/heart_rate/blood_pressure/blood_glucose/sleep_duration/...
    String metricLabel,                 // 体重/心率/血压/血糖/睡眠时长/...
    String preferredDeviceType,         // scale/watch/bp_monitor/cgm/sleep_monitor/pulse_ox/thermometer/rehab_sensor
    String fallbackDeviceType,          // 默认 manual
    String pillar,                      // physical/body/sleep/rehab
    String icon,                        // iconify 图标名（如 solar:scale-outline）
    int sortOrder
) {}
