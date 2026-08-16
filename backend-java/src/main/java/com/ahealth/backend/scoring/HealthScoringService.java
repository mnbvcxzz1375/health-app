package com.ahealth.backend.scoring;

import com.ahealth.backend.common.CurrentUser;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthScoringService {
  private final JdbcTemplate jdbc;

  public HealthScoringService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public ScoringDtos.HealthScoreResponse getScore() {
    long uid = CurrentUser.requireUserId();

    var latest = jdbc.queryForList(
        "SELECT hr,sleep_score,stress_score,systolic_bp,diastolic_bp,deep_sleep_hours,awake_times,"
        + "vo2_max,exercise_minutes,stand_hours,active_energy_kcal,flights_climbed,hrv_millis,"
        + "mindful_minutes,steps FROM monitor_records WHERE user_id=? ORDER BY recorded_at DESC LIMIT 1", uid);
    var baseline = jdbc.queryForList(
        "SELECT AVG(hr) as a_hr, AVG(sleep_score) as a_sleep, AVG(stress_score) as a_stress,"
        + " AVG(systolic_bp) as a_systolic, AVG(diastolic_bp) as a_diastolic, AVG(vo2_max) as a_vo2,"
        + " AVG(exercise_minutes) as a_ex, AVG(hrv_millis) as a_hrv"
        + " FROM monitor_records WHERE user_id=? AND recorded_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)", uid);
    var trend7d = jdbc.queryForList(
        "SELECT ROUND(AVG(hr),0) as a_hr, ROUND(AVG(sleep_score),0) as a_sleep, ROUND(AVG(stress_score),0) as a_stress,"
        + " ROUND(AVG(vo2_max),1) as a_vo2, ROUND(AVG(exercise_minutes),0) as a_ex, ROUND(AVG(hrv_millis),0) as a_hrv"
        + " FROM monitor_records WHERE user_id=? AND recorded_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)", uid);

    if (latest.isEmpty()) {
      return noDataResponse("暂无可用的监测记录，请先同步设备或手动录入数据。", "none");
    }
    int observedMetrics = countObservedMetrics(latest.get(0));
    if (observedMetrics < 2) {
      return noDataResponse("当前监测数据不足，暂不生成综合健康评分。", "insufficient");
    }

    int hr = safeInt(latest, 0, "hr", 0);
    int sleep = safeInt(latest, 0, "sleep_score", 0);
    int stress = safeInt(latest, 0, "stress_score", 0);
    int systolic = safeInt(latest, 0, "systolic_bp", 0);
    int diastolic = safeInt(latest, 0, "diastolic_bp", 0);
    double vo2 = safeDouble(latest, 0, "vo2_max", 0);
    int exMin = safeInt(latest, 0, "exercise_minutes", 0);
    int standH = safeInt(latest, 0, "stand_hours", 0);
    int activeKcal = safeInt(latest, 0, "active_energy_kcal", 0);
    int flights = safeInt(latest, 0, "flights_climbed", 0);
    int hrv = safeInt(latest, 0, "hrv_millis", 0);
    int mindful = safeInt(latest, 0, "mindful_minutes", 0);
    int steps = safeInt(latest, 0, "steps", 0);

    boolean hrAvailable = hr > 0;
    boolean sleepAvailable = sleep > 0;
    boolean stressAvailable = stress > 0;
    boolean vo2Available = vo2 > 0;
    boolean exerciseAvailable = exMin > 0;
    boolean standEnergyAvailable = standH > 0 || activeKcal > 0;
    boolean recoveryAvailable = hrv > 0;
    boolean activityAvailable = steps > 0 || flights > 0;
    boolean bloodPressureAvailable = systolic > 0 && diastolic > 0;

    double bHr = safeDouble(baseline, 0, "a_hr", hr);
    double bSleep = safeDouble(baseline, 0, "a_sleep", sleep);
    double bStress = safeDouble(baseline, 0, "a_stress", stress);
    double bVo2 = safeDouble(baseline, 0, "a_vo2", vo2);
    double bEx = safeDouble(baseline, 0, "a_ex", exMin);
    double bHrv = safeDouble(baseline, 0, "a_hrv", hrv);

    double tHr = safeDouble(trend7d, 0, "a_hr", hr);
    double tSleep = safeDouble(trend7d, 0, "a_sleep", sleep);
    double tStress = safeDouble(trend7d, 0, "a_stress", stress);
    double tVo2 = safeDouble(trend7d, 0, "a_vo2", vo2);
    double tEx = safeDouble(trend7d, 0, "a_ex", exMin);

    // === 维度评分 ===

    // 心率 (权重 0.13) — 年龄调整 + HRV 融合
    int age = getUserAge(uid);
    double optimalRhr = 70.0 - (age - 30) * 0.3;
    optimalRhr = clamp(optimalRhr, 55, 80); // bound to reasonable range

    double hrDeviation = Math.abs(hr - optimalRhr);
    double hrPenalty;
    if (hrDeviation <= 5) hrPenalty = 1.0;
    else if (hrDeviation <= 15) hrPenalty = 1.5;
    else if (hrDeviation <= 25) hrPenalty = 2.5;
    else hrPenalty = 4.0;

    double hrPop = clamp(100 - hrDeviation * hrPenalty, 0, 100);
    double bHrDeviation = Math.abs(bHr - optimalRhr);
    double hrBase = clamp(100 - bHrDeviation * hrPenalty * 0.8, 0, 100);
    double hrTrend = clamp(100 - Math.abs(tHr - hr) * 3, 0, 100);

    // HRV bonus: RMSSD indicates autonomic health
    double hrvBonus = clamp((hrv - 30) * 0.3, -10, 15);
    // Weights: 0.4 + 0.35 + 0.25 = 1.0, hrvBonus applied as scaling factor
    double hrFinal = (hrPop * 0.4 + hrBase * 0.35 + hrTrend * 0.25) * (1.0 + hrvBonus / 100.0);
    hrFinal = clamp(hrFinal, 0, 100);

    // 睡眠 (权重 0.18) — 复合评分算法
    double deepSleepHours = safeDouble(latest, 0, "deep_sleep_hours", 0);
    int awakeTimes = safeInt(latest, 0, "awake_times", 0);

    // Component 1: Raw sleep score (40% of sleep dimension)
    double slRaw = sleep;

    // Component 2: Deep sleep quality (25% of sleep dimension)
    // Ideal: 1.5-2.5 hours deep sleep for 7-8h total
    double deepRatio = deepSleepHours > 0 ? clamp(deepSleepHours / 2.0 * 100, 0, 100) : 0;
    double slDeep = deepRatio;

    // Component 3: Sleep continuity (20% of sleep dimension)
    // Each awakening reduces score; ideal = 0-1 awakenings
    double slContinuity = clamp(100 - awakeTimes * 15, 0, 100);

    // Component 4: Sleep regularity (15% of sleep dimension)
    // Based on 7-day sleep score consistency
    var sleep7d = jdbc.queryForList(
        "SELECT sleep_score FROM monitor_records WHERE user_id=? AND recorded_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) ORDER BY recorded_at", uid);
    double slRegularity = sleep > 0 ? 80 : 0;
    if (sleep7d.size() >= 3) {
      double mean = sleep7d.stream().mapToDouble(r -> safeDouble(List.of(r), 0, "sleep_score", 0)).average().orElse(0);
      double variance = sleep7d.stream()
          .mapToDouble(r -> Math.pow(safeDouble(List.of(r), 0, "sleep_score", 0) - mean, 2))
          .average().orElse(0);
      double stdDev = Math.sqrt(variance);
      // Lower std dev = more regular = higher score
      slRegularity = clamp(100 - stdDev * 3, 0, 100);
    }

    // Composite sleep score
    double slPop = slRaw * 0.40 + slDeep * 0.25 + slContinuity * 0.20 + slRegularity * 0.15;
    double slBase = bSleep;
    double slTrend = clamp(100 - Math.abs(tSleep - sleep) * 2, 0, 100);
    double slFinal = slPop * 0.4 + slBase * 0.35 + slTrend * 0.25;

    // 压力 (权重 0.13) — HRV 驱动的压力评估
    // Component 1: RMSSD-based parasympathetic score (40%)
    double rmssdScore;
    if (hrv <= 0) {
      rmssdScore = 50; // no HRV data, neutral
    } else if (hrv >= 60) {
      rmssdScore = 92 + clamp((hrv - 60) / 40.0, 0, 1) * 8; // 92-100
    } else if (hrv >= 40) {
      rmssdScore = 70 + ((hrv - 40) / 20.0) * 22; // 70-92
    } else if (hrv >= 25) {
      rmssdScore = 45 + ((hrv - 25) / 15.0) * 25; // 45-70
    } else {
      rmssdScore = clamp(15 + (hrv / 25.0) * 30, 0, 45); // 0-45
    }

    // Component 2: HRV recovery trend (35%)
    // Compare 7-day average HRV with 30-day average
    double bHrvVal = safeDouble(baseline, 0, "a_hrv", hrv);
    double tHrvVal = safeDouble(trend7d, 0, "a_hrv", hrv);
    double hrvTrendRatio = bHrvVal > 0 ? tHrvVal / bHrvVal : 1.0;
    double recoveryTrend;
    if (hrvTrendRatio >= 1.1) recoveryTrend = 90; // improving
    else if (hrvTrendRatio >= 0.95) recoveryTrend = 75; // stable
    else if (hrvTrendRatio >= 0.8) recoveryTrend = 55; // declining
    else recoveryTrend = 35; // significantly declining

    // Component 3: Raw stress score (25%)
    double stRaw = 100 - stress;

    // Composite stress score
    double stPop = rmssdScore * 0.40 + recoveryTrend * 0.35 + stRaw * 0.25;
    double stBase = 100 - bStress;
    double stTrend = clamp(100 - Math.abs(tStress - stress) * 2, 0, 100);
    double stFinal = stPop * 0.4 + stBase * 0.35 + stTrend * 0.25;

    // VO2Max (权重 0.10)
    double vo2Score = scoreVO2Max(vo2);
    double vo2Base = scoreVO2Max(bVo2 > 0 ? bVo2 : vo2);
    double vo2Trend = clamp(100 - Math.abs(tVo2 - vo2) * 3, 0, 100);
    double vo2Final = vo2Score * 0.4 + vo2Base * 0.35 + vo2Trend * 0.25;

    // 锻炼时间 (权重 0.10)
    double exScore = scoreExerciseMinutes(exMin);
    double exBase = scoreExerciseMinutes((int) bEx);
    double exTrend = clamp(100 - Math.abs(tEx - exMin) * 2, 0, 100);
    double exFinal = exScore * 0.4 + exBase * 0.35 + exTrend * 0.25;

    // 站立+活动能量 (权重 0.10)
    double standScore = scoreStandHours(standH);
    double energyScore = scoreActiveEnergy(activeKcal);
    double seFinal = standScore * 0.5 + energyScore * 0.5;

    // HRV+恢复 (权重 0.05)
    double hrvScore = scoreHRV(hrv);
    double hrvBase = scoreHRV((int) bHrv);
    double recFinal = hrvScore * 0.6 + (100 - stress) * 0.4;
    recFinal = recFinal * 0.5 + hrvBase * 0.5;

    // 活动量 (权重 0.13) — 步数 + 飞行楼层
    double stepScore = scoreSteps(steps);
    double flightScore = scoreFlights(flights);
    double actFinal = stepScore * 0.7 + flightScore * 0.3;

    // 血压 (权重 0.08)
    double bpScore = scoreBloodPressure(systolic, diastolic);
    double bSystolic = safeDouble(baseline, 0, "a_systolic", systolic);
    double bDiastolic = safeDouble(baseline, 0, "a_diastolic", diastolic);
    double bpBase = scoreBloodPressure((int) bSystolic, (int) bDiastolic);
    double bpFinal = bpScore * 0.5 + bpBase * 0.5;

    // 用药依从性 (权重 0.07)
    MedicationScore medicationScore = scoreMedicationAdherence(uid);
    double adherenceScore = medicationScore.score();
    boolean adherenceAvailable = medicationScore.available();
    double adhFinal = adherenceScore;

    // === 总分：只对有观测值的维度归一化，缺失维度不得贡献默认分 ===
    double weightedScore = 0;
    double availableWeight = 0;
    if (hrAvailable) { weightedScore += hrFinal * 0.13; availableWeight += 0.13; }
    if (sleepAvailable) { weightedScore += slFinal * 0.18; availableWeight += 0.18; }
    if (stressAvailable) { weightedScore += stFinal * 0.13; availableWeight += 0.13; }
    if (vo2Available) { weightedScore += vo2Final * 0.09; availableWeight += 0.09; }
    if (exerciseAvailable) { weightedScore += exFinal * 0.09; availableWeight += 0.09; }
    if (standEnergyAvailable) { weightedScore += seFinal * 0.09; availableWeight += 0.09; }
    if (recoveryAvailable) { weightedScore += recFinal * 0.05; availableWeight += 0.05; }
    if (activityAvailable) { weightedScore += actFinal * 0.13; availableWeight += 0.13; }
    if (bloodPressureAvailable) { weightedScore += bpFinal * 0.08; availableWeight += 0.08; }
    if (adherenceAvailable) { weightedScore += adhFinal * 0.07; availableWeight += 0.07; }
    double overall = availableWeight > 0 ? weightedScore / availableWeight : 0;
    String risk = availableWeight <= 0 ? "unknown" : overall >= 80 ? "low" : overall >= 60 ? "medium" : "high";

    List<ScoringDtos.CategoryScore> cats = new ArrayList<>();
    cats.add(cat("heartRate", "静息心率", hrFinal, hr, bHr, 0.13, "heart_rate",
        String.format("年龄调整最优值 %.0f bpm，HRV修正 +%.1f%%", optimalRhr, hrvBonus), hrAvailable));
    cats.add(cat("sleep", "睡眠质量", slFinal, sleep, bSleep, 0.18, "sleep_debt",
        String.format("当前评分 %d，30天均值 %.0f，7天趋势 %.0f", sleep, bSleep, tSleep), sleepAvailable));
    cats.add(cat("stress", "压力负荷", stFinal, 100 - stress, 100 - bStress, 0.13, "stress_elevated",
        String.format("RMSSD %d ms，恢复趋势 %.0f%%，自主神经评分 %.0f", hrv, recoveryTrend, rmssdScore), stressAvailable));
    cats.add(cat("vo2Max", "最大摄氧量", vo2Final, vo2, bVo2, 0.09, "vo2_low",
        String.format("VO2Max %.1f ml/kg/min，30天均值 %.1f", vo2, bVo2), vo2Available));
    cats.add(cat("exercise", "锻炼时间", exFinal, exMin, bEx, 0.09, "exercise_deficit",
        String.format("当前 %d 分钟，30天均值 %.0f 分钟", exMin, bEx), exerciseAvailable));
    cats.add(cat("standEnergy", "站立与活动", seFinal, standH, 0, 0.09, "sedentary",
        String.format("站立 %d 小时 + 活动能量 %d kcal", standH, activeKcal), standEnergyAvailable));
    cats.add(cat("recovery", "恢复状态", recFinal, hrv, bHrv, 0.05, "recovery_low",
        String.format("HRV %d ms（RMSSD），30天均值 %.0f ms", hrv, bHrv), recoveryAvailable));
    cats.add(cat("activity", "步行活动", actFinal, steps, 0, 0.13, "activity_low",
        String.format("步数 %d + 楼层 %d", steps, flights), activityAvailable));
    cats.add(cat("bloodPressure", "血压", bpFinal, systolic, bSystolic, 0.08, "bp_elevated",
        String.format("收缩压 %d / 舒张压 %d mmHg", systolic, diastolic), bloodPressureAvailable));
    cats.add(cat("medAdherence", "用药依从性", adhFinal, adherenceScore, 100, 0.07, "medication_nonadherence",
        String.format("今日依从性 %.0f%%（基于服药记录）", adherenceScore), adherenceAvailable));

    List<ScoringDtos.TopRisk> topRisks = new ArrayList<>();
    for (ScoringDtos.CategoryScore c : cats) {
      if (c.dataAvailable() && c.score() < 70) {
        topRisks.add(new ScoringDtos.TopRisk(
            c.attentionType(),
            c.label(),
            String.format("%s评分 %d 分，%s", c.label(), c.score(), c.riskNote()),
            c.score() < 50 ? 3 : c.score() < 60 ? 2 : 1
        ));
      }
    }
    topRisks.sort((a, b) -> Integer.compare(b.severity(), a.severity()));

    List<String> actions = new ArrayList<>();
    if (hrAvailable && hrFinal < 70) actions.add("关注心率恢复，适当减少运动强度");
    if (sleepAvailable && slFinal < 70) actions.add("改善睡眠习惯，保持规律作息");
    if (stressAvailable && stFinal < 70) actions.add("增加放松训练，尝试深呼吸或冥想");
    if (vo2Available && vo2Final < 60) actions.add("VO2Max 偏低，建议增加有氧运动频次");
    if (exerciseAvailable && exFinal < 60) actions.add("锻炼时间不足，建议每天至少 30 分钟中等强度运动");
    if (standEnergyAvailable && seFinal < 60) actions.add("站立时间不足，建议每小时起身活动");
    if (recoveryAvailable && recFinal < 60) actions.add("恢复指标偏低，建议优先保证睡眠和正念练习");
    if (activityAvailable && actFinal < 60) actions.add("活动量偏低，建议增加日常步行");
    if (bloodPressureAvailable && bpFinal < 70) actions.add("血压偏高，建议低盐饮食并定期监测");
    if (adherenceAvailable && adhFinal < 80) actions.add("用药依从性不足，请按时服药并记录");
    if (actions.isEmpty()) actions.add(dataQualityWarning(observedMetrics));

    String summary = String.format("综合评分 %.0f 分，风险等级：%s%s", overall, riskLevelCN(risk),
        observedMetrics >= 8 ? "" : "（仅基于当前可用指标）");
    String dataQuality = observedMetrics >= 8 ? "complete" : "partial";
    List<String> dataWarnings = dataQuality.equals("complete")
        ? List.of()
        : List.of("当前评分仅基于部分监测指标，缺失指标不会被视为正常。请继续同步数据后再比较趋势。");
    return new ScoringDtos.HealthScoreResponse(
        round(overall), risk, cats, topRisks, actions, summary, dataQuality, dataWarnings);
  }

  private ScoringDtos.HealthScoreResponse noDataResponse(String message, String quality) {
    return new ScoringDtos.HealthScoreResponse(
        0,
        "unknown",
        List.of(),
        List.of(new ScoringDtos.TopRisk(
            "NO_MONITOR_DATA", "监测数据不足", message, 1)),
        List.of("先同步 Apple Health 或智能穿戴数据，再生成个性化健康评分。"),
        message,
        quality,
        List.of(message)
    );
  }

  private int countObservedMetrics(Map<String, Object> row) {
    String[] keys = {
        "hr", "sleep_score", "stress_score", "systolic_bp", "diastolic_bp", "vo2_max",
        "exercise_minutes", "stand_hours", "active_energy_kcal", "flights_climbed",
        "hrv_millis", "mindful_minutes", "steps"
    };
    int count = 0;
    for (String key : keys) {
      Object value = row.get(key);
      if (value instanceof Number number && number.doubleValue() > 0) count++;
    }
    return count;
  }

  private double scoreVO2Max(double vo2) {
    if (vo2 <= 0) return 75;
    double x = clamp(vo2, 15, 65);
    if (x >= 48) return 96;
    if (x >= 42) return 85 + ((x - 42) / 6) * 11;
    if (x >= 36) return 70 + ((x - 36) / 6) * 15;
    if (x >= 30) return 50 + ((x - 30) / 6) * 20;
    return clamp(20 + ((x - 15) / 15) * 30, 0, 100);
  }

  private double scoreExerciseMinutes(int minutes) {
    if (minutes <= 0) return 40;
    double x = clamp(minutes, 0, 120);
    if (x >= 30) return 90 + clamp((x - 30) / 60, 0, 1) * 10;
    if (x >= 15) return 70 + ((x - 15) / 15) * 20;
    if (x >= 5) return 45 + ((x - 5) / 10) * 25;
    return clamp(10 + (x / 5) * 35, 0, 100);
  }

  private double scoreStandHours(int hours) {
    if (hours <= 0) return 40;
    double x = clamp(hours, 0, 16);
    if (x >= 10) return 95;
    if (x >= 8) return 80 + ((x - 8) / 2) * 15;
    if (x >= 5) return 55 + ((x - 5) / 3) * 25;
    return clamp(10 + (x / 5) * 45, 0, 100);
  }

  private double scoreActiveEnergy(int kcal) {
    if (kcal <= 0) return 50;
    double x = clamp(kcal, 0, 1000);
    if (x >= 300) return 90 + clamp((x - 300) / 700, 0, 1) * 10;
    if (x >= 150) return 70 + ((x - 150) / 150) * 20;
    return clamp(20 + (x / 150) * 50, 0, 100);
  }

  private double scoreHRV(int ms) {
    if (ms <= 0) return 70;
    double x = clamp(ms, 5, 200);
    if (x >= 60) return 92;
    if (x >= 40) return 75 + ((x - 40) / 20) * 17;
    if (x >= 25) return 55 + ((x - 25) / 15) * 20;
    return clamp(20 + ((x - 5) / 20) * 35, 0, 100);
  }

  private double scoreSteps(int steps) {
    if (steps <= 0) return 40;
    double x = clamp(steps, 0, 20000);
    if (x >= 8000) return 94 + clamp((x - 8000) / 4000, 0, 1) * 6;
    if (x >= 5000) return 80 + ((x - 5000) / 3000) * 14;
    if (x >= 2000) return 60 + ((x - 2000) / 3000) * 20;
    return clamp(20 + (x / 2000) * 40, 0, 100);
  }

  private double scoreFlights(int flights) {
    if (flights <= 0) return 50;
    double x = clamp(flights, 0, 30);
    if (x >= 10) return 95;
    if (x >= 5) return 75 + ((x - 5) / 5) * 20;
    return clamp(30 + (x / 5) * 45, 0, 100);
  }

  private double scoreBloodPressure(int systolic, int diastolic) {
    if (systolic <= 0 || diastolic <= 0) return 0; // incomplete pair is not a normal reading
    // Based on AHA guidelines: normal <120/<80, elevated 120-129/<80, high >=130/>=80
    if (systolic < 120 && diastolic < 80) return 95;
    if (systolic < 130 && diastolic < 80) return 80;
    if (systolic < 140 && diastolic < 90) return 60;
    if (systolic < 160 && diastolic < 100) return 40;
    return 20;
  }

  private MedicationScore scoreMedicationAdherence(long uid) {
    try {
      Integer activeMedicationCount = jdbc.queryForObject(
          "SELECT COUNT(*) FROM medications WHERE user_id=? AND enabled=1", Integer.class, uid);
      if (activeMedicationCount == null || activeMedicationCount <= 0) {
        return new MedicationScore(0, false);
      }
      String today = java.time.LocalDate.now().toString();
      var rows = jdbc.queryForList(
          "SELECT status FROM medication_intake_log WHERE user_id=? AND intake_date=?", uid, today);
      if (rows.isEmpty()) return new MedicationScore(0, false);

      int taken = 0;
      int total = rows.size();
      for (var r : rows) {
        String status = (String) r.get("status");
        if ("taken".equals(status) || "half".equals(status)) taken++;
      }
      return new MedicationScore(clamp(60 + (taken / (double) total) * 40, 0, 100), true);
    } catch (Exception e) {
      return new MedicationScore(0, false);
    }
  }

  private ScoringDtos.CategoryScore cat(String key, String label, double score, double current,
      double baseline, double weight, String attentionType, String algorithmNote,
      boolean dataAvailable) {
    return new ScoringDtos.CategoryScore(key, label, round(score),
        current, baseline, round(current - baseline),
        dataAvailable ? riskNote(score) : "数据不足", attentionType, weight, algorithmNote, dataAvailable);
  }

  private String dataQualityWarning(int observedMetrics) {
    return observedMetrics < 2
        ? "监测指标不足，暂不生成个性化建议。"
        : "暂未发现可操作的低风险指标；请继续同步完整监测数据后再比较趋势。";
  }

  private record MedicationScore(double score, boolean available) {}

  private double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

  private int getUserAge(long uid) {
    try {
      Integer age = jdbc.queryForObject(
          "SELECT age FROM user_settings WHERE user_id=?", Integer.class, uid);
      return age != null ? age : 30;
    } catch (Exception e) {
      return 30; // default
    }
  }

  private int round(double v) { return (int) Math.round(v); }
  private int safeInt(List<Map<String, Object>> rows, int idx, String col, int def) {
    if (rows.size() <= idx) return def;
    Object v = rows.get(idx).get(col);
    return v instanceof Number n ? n.intValue() : def;
  }
  private double safeDouble(List<Map<String, Object>> rows, int idx, String col, double def) {
    if (rows.size() <= idx) return def;
    Object v = rows.get(idx).get(col);
    return v instanceof Number n ? n.doubleValue() : def;
  }
  private String riskNote(double score) {
    if (score >= 80) return "正常";
    if (score >= 60) return "需要关注";
    return "高风险";
  }
  private String riskLevelCN(String risk) {
    return switch (risk) {
      case "low" -> "低风险";
      case "medium" -> "中等风险";
      case "high" -> "高风险";
      default -> risk;
    };
  }
}
