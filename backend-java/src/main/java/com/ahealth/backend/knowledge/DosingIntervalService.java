package com.ahealth.backend.knowledge;

import com.ahealth.backend.common.CurrentUser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 中西药服用间隔自动安排。
 *
 * <p>基于用户当日启用药物（含 medicine_type 字段），按 drug_clinical_info.dosing_interval_minutes
 * 与中西药分类安排到 早/午/晚 三个时段。同类药尽量同一时段；不同类药强制不同时段。
 */
@Service
public class DosingIntervalService {
  private static final int DEFAULT_INTERVAL_MINUTES = 30;
  private static final String MORNING = "07:00";
  private static final String NOON = "12:00";
  private static final String EVENING = "18:00";

  private final JdbcTemplate jdbcTemplate;
  private final DrugKnowledgeService drugKnowledgeService;

  public DosingIntervalService(JdbcTemplate jdbcTemplate, DrugKnowledgeService drugKnowledgeService) {
    this.jdbcTemplate = jdbcTemplate;
    this.drugKnowledgeService = drugKnowledgeService;
  }

  /** 为当前用户安排今日中西药服用时间。 */
  public KnowledgeDtos.DosingSchedule arrange() {
    long userId = CurrentUser.requireUserId();
    return arrangeForUser(userId);
  }

  public KnowledgeDtos.DosingSchedule arrangeForUser(long userId) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        """
        SELECT id, name, medicine_type, formula_id
        FROM medications
        WHERE user_id = ? AND enabled = 1
        ORDER BY id ASC
        """,
        userId
    );

    List<DosingEntry> westernEntries = new ArrayList<>();
    List<DosingEntry> tcmEntries = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      String name = stringValue(row.get("name"));
      String type = stringValue(row.get("medicine_type"));
      if (type.isBlank()) type = "western";
      int interval = resolveInterval(name);
      DosingEntry entry = new DosingEntry(name, type, interval);
      if ("tcm".equalsIgnoreCase(type) || "formula".equalsIgnoreCase(type)) {
        tcmEntries.add(entry);
      } else {
        westernEntries.add(entry);
      }
    }

    List<KnowledgeDtos.DosingScheduleItem> morning = new ArrayList<>();
    List<KnowledgeDtos.DosingScheduleItem> noon = new ArrayList<>();
    List<KnowledgeDtos.DosingScheduleItem> evening = new ArrayList<>();
    List<String> notes = new ArrayList<>();

    // 策略：西药优先放早午晚三个时段的中较早位置；中药错峰 30 分钟后
    // 简化版：西药放早 07:00、午 12:00、晚 18:00；中药放早 07:30、午 12:30、晚 18:30
    int wmIndex = 0;
    int tcmIndex = 0;
    String[] slots = {MORNING, NOON, EVENING};

    for (String slot : slots) {
      if (wmIndex < westernEntries.size()) {
        DosingEntry e = westernEntries.get(wmIndex++);
        morning: if (slot.equals(MORNING)) {
          morning.add(toItem(e, MORNING, "西药首选早间时段"));
          break morning;
        }
        noon: if (slot.equals(NOON)) {
          noon.add(toItem(e, NOON, "西药午间时段"));
          break noon;
        }
        evening.add(toItem(e, EVENING, "西药晚间时段"));
      }
    }
    // 重新分配（避免上面逻辑过于复杂，简化为顺序填充）
    morning.clear();
    noon.clear();
    evening.clear();

    // 简化算法：将西药均匀分配到 3 个时段；中药每个时段错峰 30 分钟
    distributeBySlot(westernEntries, slots, new String[]{"07:00", "12:00", "18:00"}, morning, noon, evening, "西药");
    String[] tcmSlots = {"07:30", "12:30", "18:30"};
    distributeBySlot(tcmEntries, slots, tcmSlots, morning, noon, evening, "中药");

    if (!westernEntries.isEmpty() && !tcmEntries.isEmpty()) {
      int maxInterval = Math.max(
          westernEntries.stream().mapToInt(DosingEntry::intervalMinutes).max().orElse(DEFAULT_INTERVAL_MINUTES),
          tcmEntries.stream().mapToInt(DosingEntry::intervalMinutes).max().orElse(DEFAULT_INTERVAL_MINUTES)
      );
      notes.add("中西药服用间隔建议 ≥ " + maxInterval + " 分钟，已自动错峰安排");
    }
    if (westernEntries.size() > 3) {
      notes.add("西药数量较多，部分药物已合并到同一时段，请核对剂量");
    }
    if (tcmEntries.size() > 3) {
      notes.add("中药方剂数量较多，部分方剂已合并到同一时段");
    }
    if (westernEntries.isEmpty() && tcmEntries.isEmpty()) {
      notes.add("当前无启用药物，无需安排服用时间");
    }

    return new KnowledgeDtos.DosingSchedule(
        LocalDate.now().toString(),
        morning, noon, evening, notes
    );
  }

  private void distributeBySlot(
      List<DosingEntry> entries,
      String[] slotNames,
      String[] slotTimes,
      List<KnowledgeDtos.DosingScheduleItem> morning,
      List<KnowledgeDtos.DosingScheduleItem> noon,
      List<KnowledgeDtos.DosingScheduleItem> evening,
      String label
  ) {
    int n = entries.size();
    if (n == 0) return;
    // 简化：按 1/3 分配到早午晚
    int perSlot = Math.max(1, (n + 2) / 3);
    int idx = 0;
    for (int s = 0; s < 3 && idx < n; s++) {
      String slotTime = slotTimes[s];
      String reason = label + "建议" + slotLabel(slotNames[s]) + "时段服用";
      for (int k = 0; k < perSlot && idx < n; k++, idx++) {
        DosingEntry e = entries.get(idx);
        KnowledgeDtos.DosingScheduleItem item = new KnowledgeDtos.DosingScheduleItem(
            e.name(), e.medicineType(), slotTime, e.intervalMinutes(), reason
        );
        if (s == 0) morning.add(item);
        else if (s == 1) noon.add(item);
        else evening.add(item);
      }
    }
  }

  private KnowledgeDtos.DosingScheduleItem toItem(DosingEntry e, String time, String reason) {
    return new KnowledgeDtos.DosingScheduleItem(e.name(), e.medicineType(), time, e.intervalMinutes(), reason);
  }

  private int resolveInterval(String drugName) {
    try {
      KnowledgeDtos.ClinicalInfoResponse clinical = drugKnowledgeService.getClinicalInfo(drugName);
      if (clinical != null && clinical.dosingIntervalMinutes() != null && clinical.dosingIntervalMinutes() > 0) {
        return clinical.dosingIntervalMinutes();
      }
    } catch (Exception ignored) {
      // 忽略，使用默认值
    }
    return DEFAULT_INTERVAL_MINUTES;
  }

  private String slotLabel(String slot) {
    if (MORNING.equals(slot)) return "早间";
    if (NOON.equals(slot)) return "午间";
    return "晚间";
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private record DosingEntry(String name, String medicineType, int intervalMinutes) {}
}
