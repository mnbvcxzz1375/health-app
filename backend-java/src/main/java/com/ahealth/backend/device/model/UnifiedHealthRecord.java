package com.ahealth.backend.device.model;

import java.time.Instant;

/**
 * 统一健康数据记录模型。
 * 所有 Provider（OAuth 厂商 + Apple Health + Bluetooth + Manual + SDK）对外输出统一为此结构。
 * 字段命名借鉴 ROOK 约定（xxxInt/xxxFloat 后缀），便于阅读与映射。
 */
public record UnifiedHealthRecord(
    String provider,                    // 数据来源 provider：garmin/oura/apple_health/manual/sdk/...
    String sourceDevice,                // 设备型号（如 "Oura Ring Gen 3"）
    Instant recordedAt,                 // 数据采集时间
    // ===== Physical Health =====
    Integer heartRateAvgBpmInt,         // 平均心率 bpm
    Integer heartRateRestingBpmInt,     // 静息心率 bpm
    Integer hrvMillisInt,               // 心率变异性 ms（RMSSD）
    Integer stepsInt,                   // 步数
    Integer exerciseMinutesInt,         // 锻炼分钟
    Integer standHoursInt,              // 站立小时
    Integer activeEnergyKcalInt,        // 活动能量 kcal
    Integer flightsClimbedInt,          // 爬楼层数
    Double vo2MaxFloat,                 // 最大摄氧量
    Integer stressScoreInt,             // 压力评分 0-100
    // ===== Body Health =====
    Double weightKgFloat,               // 体重 kg
    Double heightCmFloat,               // 身高 cm
    Double bmiFloat,                    // BMI
    Integer systolicBpInt,              // 收缩压 mmHg
    Integer diastolicBpInt,             // 舒张压 mmHg
    Double bloodGlucoseFloat,           // 血糖 mmol/L
    Double bodyTemperatureFloat,        // 体温 ℃
    Integer spo2Int,                    // 血氧 %
    Integer respiratoryRateInt,         // 呼吸频率 次/分
    // ===== Sleep Health =====
    Double sleepDurationHoursFloat,     // 总睡眠时长（小时）
    Double deepSleepHoursFloat,         // 深睡时长（小时）
    Double remSleepHoursFloat,          // REM 睡眠时长（小时）
    Integer sleepScoreInt,              // 睡眠评分 0-100
    Integer awakeTimesInt,              // 夜醒次数
    Integer mindfulMinutesInt           // 正念分钟
) {
  /** 构造一个所有字段为 null 的空记录（仅 provider/sourceDevice/recordedAt 必填）。 */
  public static UnifiedHealthRecord empty(String provider, String sourceDevice, Instant recordedAt) {
    return new UnifiedHealthRecord(
        provider, sourceDevice, recordedAt,
        null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null
    );
  }
}
