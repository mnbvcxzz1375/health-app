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
        "SELECT hr,sleep_score,stress_score,deep_sleep_hours,vo2_max,exercise_minutes,stand_hours,"
        + "active_energy_kcal,flights_climbed,hrv_millis,mindful_minutes,steps FROM monitor_records ORDER BY recorded_at DESC LIMIT 1");
    var baseline = jdbc.queryForList(
        "SELECT AVG(hr) as a_hr, AVG(sleep_score) as a_sleep, AVG(stress_score) as a_stress,"
        + " AVG(vo2_max) as a_vo2, AVG(exercise_minutes) as a_ex, AVG(hrv_millis) as a_hrv,"
        + " AVG(systolic_bp) as a_systolic, AVG(diastolic_bp) as a_diastolic"
        + " FROM monitor_records WHERE recorded_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)");
    var trend7d = jdbc.queryForList(
        "SELECT ROUND(AVG(hr),0) as a_hr, ROUND(AVG(sleep_score),0) as a_sleep, ROUND(AVG(stress_score),0) as a_stress,"
        + " ROUND(AVG(vo2_max),1) as a_vo2, ROUND(AVG(exercise_minutes),0) as a_ex"
        + " FROM monitor_records WHERE recorded_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)");

    int hr = safeInt(latest, 0, "hr", 72);
    int sleep = safeInt(latest, 0, "sleep_score", 76);
    int stress = safeInt(latest, 0, "stress_score", 50);
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

    double bHr = safeDouble(baseline, 0, "a_hr", 72);
    double bSleep = safeDouble(baseline, 0, "a_sleep", 76);
    double bStress = safeDouble(baseline, 0, "a_stress", 50);
    double bVo2 = safeDouble(baseline, 0, "a_vo2", vo2);
    double bEx = safeDouble(baseline, 0, "a_ex", exMin);
    double bHrv = safeDouble(baseline, 0, "a_hrv", hrv);

    double tHr = safeDouble(trend7d, 0, "a_hr", hr);
    double tSleep = safeDouble(trend7d, 0, "a_sleep", sleep);
    double tStress = safeDouble(trend7d, 0, "a_stress", stress);
    double tVo2 = safeDouble(trend7d, 0, "a_vo2", vo2);
    double tEx = safeDouble(trend7d, 0, "a_ex", exMin);

    // === 维度评分 ===

    // 心率 (权重 0.15)
    double hrPop = clamp(100 - Math.abs(hr - 65) * 1.5, 0, 100);
    double hrBase = clamp(100 - Math.abs(bHr - 65) * 1.2, 0, 100);
    double hrTrend = clamp(100 - Math.abs(tHr - hr) * 3, 0, 100);
    double hrFinal = hrPop * 0.4 + hrBase * 0.35 + hrTrend * 0.25;

    // 睡眠 (权重 0.20)
    double slPop = sleep;
    double slBase = bSleep;
    double slTrend = clamp(100 - Math.abs(tSleep - sleep) * 2, 0, 100);
    double slFinal = slPop * 0.4 + slBase * 0.35 + slTrend * 0.25;

    // 压力 (权重 0.15)
    double stPop = 100 - stress;
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
    double adherenceScore = scoreMedicationAdherence(uid);
    double adhFinal = adherenceScore;

    // === 总分 (10 dimensions, weights sum to 1.0) ===
    double overall = hrFinal * 0.13 + slFinal * 0.18 + stFinal * 0.13
        + vo2Final * 0.09 + exFinal * 0.09 + seFinal * 0.09
        + recFinal * 0.05 + actFinal * 0.13 + bpFinal * 0.08 + adhFinal * 0.07;
    String risk = overall >= 80 ? "low" : overall >= 60 ? "medium" : "high";

    List<ScoringDtos.CategoryScore> cats = new ArrayList<>();
    cats.add(cat("heartRate", "静息心率", hrFinal, hr, bHr, 0.13, "heart_rate"));
    cats.add(cat("sleep", "睡眠质量", slFinal, sleep, bSleep, 0.18, "sleep_debt"));
    cats.add(cat("stress", "压力负荷", stFinal, 100 - stress, 100 - bStress, 0.13, "stress_elevated"));
    cats.add(cat("vo2Max", "最大摄氧量", vo2Final, vo2, bVo2, 0.09, "vo2_low"));
    cats.add(cat("exercise", "锻炼时间", exFinal, exMin, bEx, 0.09, "exercise_deficit"));
    cats.add(cat("standEnergy", "站立与活动", seFinal, standH, 0, 0.09, "sedentary"));
    cats.add(cat("recovery", "恢复状态", recFinal, hrv, bHrv, 0.05, "recovery_low"));
    cats.add(cat("activity", "步行活动", actFinal, steps, 0, 0.13, "activity_low"));
    cats.add(cat("bloodPressure", "血压", bpFinal, systolic, bSystolic, 0.08, "bp_elevated"));
    cats.add(cat("medAdherence", "用药依从性", adhFinal, adherenceScore, 100, 0.07, "medication_nonadherence"));

    List<ScoringDtos.TopRisk> topRisks = new ArrayList<>();
    for (ScoringDtos.CategoryScore c : cats) {
      if (c.score() < 70) {
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
    if (hrFinal < 70) actions.add("关注心率恢复，适当减少运动强度");
    if (slFinal < 70) actions.add("改善睡眠习惯，保持规律作息");
    if (stFinal < 70) actions.add("增加放松训练，尝试深呼吸或冥想");
    if (vo2Final < 60) actions.add("VO2Max 偏低，建议增加有氧运动频次");
    if (exFinal < 60) actions.add("锻炼时间不足，建议每天至少 30 分钟中等强度运动");
    if (seFinal < 60) actions.add("站立时间不足，建议每小时起身活动");
    if (recFinal < 60) actions.add("恢复指标偏低，建议优先保证睡眠和正念练习");
    if (actFinal < 60) actions.add("活动量偏低，建议增加日常步行");
    if (bpFinal < 70) actions.add("血压偏高，建议低盐饮食并定期监测");
    if (adhFinal < 80) actions.add("用药依从性不足，请按时服药并记录");
    if (actions.isEmpty()) actions.add("保持当前良好状态");

    String summary = String.format("综合评分 %.0f 分，风险等级：%s", overall, riskLevelCN(risk));
    return new ScoringDtos.HealthScoreResponse(round(overall), risk, cats, topRisks, actions, summary);
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
    if (systolic <= 0 && diastolic <= 0) return 75; // no data
    // Based on AHA guidelines: normal <120/<80, elevated 120-129/<80, high >=130/>=80
    if (systolic < 120 && diastolic < 80) return 95;
    if (systolic < 130 && diastolic < 80) return 80;
    if (systolic < 140 && diastolic < 90) return 60;
    if (systolic < 160 && diastolic < 100) return 40;
    return 20;
  }

  private double scoreMedicationAdherence(long uid) {
    try {
      String today = java.time.LocalDate.now().toString();
      var rows = jdbc.queryForList(
          "SELECT status FROM medication_intake_log WHERE user_id=? AND intake_date=?", uid, today);
      if (rows.isEmpty()) return 85; // no medications to track = neutral

      int taken = 0;
      int total = rows.size();
      for (var r : rows) {
        String status = (String) r.get("status");
        if ("taken".equals(status) || "half".equals(status)) taken++;
      }
      return clamp(60 + (taken / (double) total) * 40, 0, 100);
    } catch (Exception e) {
      return 75; // table may not exist yet
    }
  }

  private ScoringDtos.CategoryScore cat(String key, String label, double score, double current, double baseline, double weight, String attentionType) {
    return new ScoringDtos.CategoryScore(key, label, round(score),
        current, baseline, round(current - baseline), riskNote(score), attentionType, weight);
  }

  private double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
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