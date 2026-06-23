package com.ahealth.backend.context;

import com.ahealth.backend.common.CurrentUser;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ContextService {
  private final JdbcTemplate jdbc;

  public ContextService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public ContextDtos.ContextSnapshot getSnapshot() {
    long uid = CurrentUser.requireUserId();
    String systemSummary = buildSystemSummary(uid);
    String dailySummary = buildDailySummary(uid);
    List<String> activeConcerns = buildActiveConcerns(uid);
    List<String> medications = getMedicationSummary(uid);
    List<ContextDtos.MemoryEntry> memories = getMemories(uid);
    ContextDtos.UserHealthBaseline baseline = buildHealthBaseline(uid);
    ContextDtos.MedicationContextSummary medSummary = buildMedicationContextSummary(uid);
    ContextDtos.InteractionMemorySummary interactionSummary = buildInteractionSummary(uid);
    return new ContextDtos.ContextSnapshot(
        systemSummary, dailySummary, activeConcerns, medications, memories,
        baseline, medSummary, interactionSummary
    );
  }

  public void saveMemory(ContextDtos.SaveMemoryRequest req) {
    long uid = CurrentUser.requireUserId();
    jdbc.update(
        "INSERT INTO user_context_memory(user_id,category,content,created_at) VALUES(?,?,?,NOW())",
        uid, req.category(), req.content()
    );
  }

  public void refreshMemory() {
    long uid = CurrentUser.requireUserId();
    // Clean up old consult memories (keep latest 50)
    jdbc.update(
        "DELETE FROM user_context_memory WHERE user_id=? AND category='consult' "
        + "AND id NOT IN (SELECT id FROM (SELECT id FROM user_context_memory WHERE user_id=? AND category='consult' ORDER BY created_at DESC LIMIT 50) AS t)",
        uid, uid
    );
  }

  private String buildSystemSummary(long uid) {
    var rows = jdbc.queryForList(
        "SELECT name,age,gender,height,weight,focus FROM user_profiles up LEFT JOIN user_settings us ON us.user_id=up.id WHERE up.id=?", uid);
    if (rows.isEmpty()) return "暂无用户数据";
    var r = rows.get(0);
    return String.format("%s，%s，年龄 %s，身高 %s cm，体重 %s kg，健康关注：%s",
        str(r.get("name")), str(r.get("gender")), str(r.get("age")),
        str(r.get("height")), str(r.get("weight")), str(r.get("focus")));
  }

  private String buildDailySummary(long uid) {
    var rows = jdbc.queryForList(
        "SELECT hr,sleep_score,stress_score,vo2_max,exercise_minutes,stand_hours,"
        + "active_energy_kcal,flights_climbed,hrv_millis,mindful_minutes,steps"
        + " FROM monitor_records ORDER BY recorded_at DESC LIMIT 1");
    if (rows.isEmpty()) return "暂无监测数据";
    var r = rows.get(0);
    List<String> parts = new ArrayList<>();
    parts.add(String.format("心率 %s 次/分", str(r.get("hr"))));
    parts.add(String.format("睡眠评分 %s", str(r.get("sleep_score"))));
    parts.add(String.format("压力指数 %s", str(r.get("stress_score"))));
    int steps = intVal(r.get("steps"));
    if (steps > 0) parts.add(String.format("步数 %d", steps));
    int exMin = intVal(r.get("exercise_minutes"));
    if (exMin > 0) parts.add(String.format("锻炼 %d 分钟", exMin));
    int standH = intVal(r.get("stand_hours"));
    if (standH > 0) parts.add(String.format("站立 %d 小时", standH));
    double vo2 = doubleVal(r.get("vo2_max"));
    if (vo2 > 0) parts.add(String.format("VO2Max %.1f", vo2));
    int activeKcal = intVal(r.get("active_energy_kcal"));
    if (activeKcal > 0) parts.add(String.format("活动能量 %d kcal", activeKcal));
    int hrv = intVal(r.get("hrv_millis"));
    if (hrv > 0) parts.add(String.format("HRV %d ms", hrv));
    int mindful = intVal(r.get("mindful_minutes"));
    if (mindful > 0) parts.add(String.format("正念 %d 分钟", mindful));
    return String.join("，", parts);
  }

  private List<String> buildActiveConcerns(long uid) {
    var rows = jdbc.queryForList(
        "SELECT hr,sleep_score,stress_score,vo2_max,exercise_minutes,stand_hours,hrv_millis,steps"
        + " FROM monitor_records ORDER BY recorded_at DESC LIMIT 1");
    List<String> concerns = new ArrayList<>();
    if (rows.isEmpty()) return concerns;
    var r = rows.get(0);
    int hr = intVal(r.get("hr"));
    int sleep = intVal(r.get("sleep_score"));
    int stress = intVal(r.get("stress_score"));
    int steps = intVal(r.get("steps"));
    int exMin = intVal(r.get("exercise_minutes"));
    int standH = intVal(r.get("stand_hours"));
    double vo2 = doubleVal(r.get("vo2_max"));
    int hrv = intVal(r.get("hrv_millis"));

    if (hr > 90) concerns.add("静息心率偏高，建议关注心血管负荷");
    if (sleep < 70) concerns.add("睡眠质量偏低，建议调整作息");
    if (stress > 65) concerns.add("压力指数偏高，建议增加放松活动");
    if (vo2 > 0 && vo2 < 35) concerns.add("最大摄氧量偏低，建议增加有氧运动");
    if (exMin > 0 && exMin < 15) concerns.add("锻炼时间不足，建议每天至少 30 分钟中等强度运动");
    if (standH > 0 && standH < 5) concerns.add("站立时间不足，建议每小时起身活动");
    if (hrv > 0 && hrv < 25) concerns.add("HRV 偏低，神经恢复状态需关注");
    if (steps > 0 && steps < 3000) concerns.add("步行量偏低，建议增加日常步行");
    return concerns;
  }

  private List<String> getMedicationSummary(long uid) {
    return jdbc.queryForList(
        "SELECT name,dosage_unit FROM medications WHERE user_id=? AND enabled=1", uid
    ).stream().map(r -> str(r.get("name")) + " " + str(r.get("dosage_unit"))).toList();
  }

  private List<ContextDtos.MemoryEntry> getMemories(long uid) {
    return jdbc.queryForList(
        "SELECT id,category,content,created_at FROM user_context_memory WHERE user_id=? ORDER BY created_at DESC LIMIT 20", uid
    ).stream().map(r -> new ContextDtos.MemoryEntry(
        ((Number)r.get("id")).longValue(),
        str(r.get("category")),
        str(r.get("content")),
        String.valueOf(r.get("created_at"))
    )).toList();
  }

  private String str(Object v) { return v == null ? "" : String.valueOf(v); }
  private int intVal(Object v) { return v instanceof Number n ? n.intValue() : 0; }
  private double doubleVal(Object v) { return v instanceof Number n ? n.doubleValue() : 0; }

  private ContextDtos.UserHealthBaseline buildHealthBaseline(long uid) {
    var baseline = jdbc.queryForList(
        "SELECT AVG(hr) as a_hr, AVG(sleep_score) as a_sleep, AVG(stress_score) as a_stress,"
        + " AVG(vo2_max) as a_vo2, AVG(steps) as a_steps"
        + " FROM monitor_records WHERE recorded_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)");
    var profile = jdbc.queryForList("SELECT risk_score, risk_level FROM user_profiles WHERE id=?", uid);

    int riskScore = 18;
    String riskLevel = "低风险";
    if (!profile.isEmpty()) {
      riskScore = intVal(profile.get(0).get("risk_score"));
      riskLevel = str(profile.get(0).get("risk_level"));
    }

    return new ContextDtos.UserHealthBaseline(
        safeInt(baseline, 0, "a_hr", 72),
        safeDouble(baseline, 0, "a_sleep", 76),
        safeDouble(baseline, 0, "a_stress", 50),
        safeDouble(baseline, 0, "a_vo2", 0),
        safeInt(baseline, 0, "a_steps", 0),
        riskScore,
        riskLevel
    );
  }

  private ContextDtos.MedicationContextSummary buildMedicationContextSummary(long uid) {
    var meds = jdbc.queryForList(
        "SELECT name,notes FROM medications WHERE user_id=? AND enabled=1", uid);
    List<String> names = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    for (var r : meds) {
      names.add(str(r.get("name")));
      String notes = str(r.get("notes"));
      if (!notes.isBlank()) warnings.add(notes);
    }
    return new ContextDtos.MedicationContextSummary(meds.size(), names, warnings);
  }

  private ContextDtos.InteractionMemorySummary buildInteractionSummary(long uid) {
    var consults = jdbc.queryForList(
        "SELECT content, created_at FROM user_context_memory WHERE user_id=? AND category='consult'"
        + " ORDER BY created_at DESC LIMIT 5", uid);
    List<String> topics = new ArrayList<>();
    String lastAt = "";
    for (var r : consults) {
      String content = str(r.get("content"));
      // Extract the question part: "用户问：XXX → ..."
      int arrow = content.indexOf("→");
      String topic = arrow > 0 ? content.substring(0, Math.min(arrow, 60)).trim() : content.substring(0, Math.min(content.length(), 60));
      topics.add(topic);
      if (lastAt.isBlank()) lastAt = str(r.get("created_at"));
    }

    int total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM user_context_memory WHERE user_id=? AND category='consult'", Integer.class, uid);

    return new ContextDtos.InteractionMemorySummary(
        total == 0 ? 0 : total, topics, lastAt
    );
  }

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
}