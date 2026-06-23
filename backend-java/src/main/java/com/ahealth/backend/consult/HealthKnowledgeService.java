package com.ahealth.backend.consult;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthKnowledgeService {
  private final JdbcTemplate jdbc;

  // Health domain keywords — questions must contain at least one to be considered health-related
  private static final Set<String> HEALTH_KEYWORDS = Set.of(
      "健康", "血压", "心率", "睡眠", "运动", "康复", "药物", "用药", "服药", "药品",
      "症状", "疼痛", "头晕", "疲劳", "饮食", "营养", "体重", "血糖", "血脂",
      "心脏", "肺", "肝", "肾", "胃", "骨", "关节", "肌肉", "神经",
      "体检", "化验", "指标", "异常", "偏高", "偏低", "正常",
      "锻炼", "步行", "跑步", "游泳", "瑜伽", "冥想", "呼吸",
      "高血压", "糖尿病", "冠心病", "失眠", "焦虑", "抑郁",
      "医生", "医院", "处方", "检查", "治疗", "手术", "康复",
      "维生素", "钙", "铁", "锌", "蛋白质", "纤维",
      "过敏", "副作用", "禁忌", "相互作用",
      "老人", "老年", "护理", "照护", "慢病"
  );

  // Non-health topics that should be rejected
  private static final Set<String> BLOCKED_TOPICS = Set.of(
      "股票", "基金", "投资", "理财", "赚钱", "贷款",
      "游戏", "电影", "电视剧", "综艺", "明星", "八卦",
      "政治", "选举", "投票", "政府",
      "编程", "代码", "软件", "开发", "bug",
      "购物", "优惠", "打折", "促销", "电商",
      "天气", "新闻", "体育", "足球", "篮球"
  );

  public HealthKnowledgeService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Check if a question is health-related.
   * Returns true if the question contains health keywords and does not match blocked topics.
   */
  public boolean isHealthRelated(String question) {
    if (question == null || question.isBlank()) return false;
    String q = question.toLowerCase();

    // Check blocked topics first
    for (String blocked : BLOCKED_TOPICS) {
      if (q.contains(blocked)) return false;
    }

    // Check health keywords
    for (String keyword : HEALTH_KEYWORDS) {
      if (q.contains(keyword)) return true;
    }

    // Short questions (<=10 chars) are likely greetings or follow-ups — allow them
    if (q.length() <= 10) return true;

    return false;
  }

  /**
   * Retrieve relevant knowledge snippets for a health question.
   * Searches user context memories and returns the most relevant ones.
   */
  public List<String> retrieveKnowledge(long uid, String question) {
    List<String> knowledge = new ArrayList<>();

    // Search user context memories for relevant content
    var memories = jdbc.queryForList(
        "SELECT content FROM user_context_memory WHERE user_id=? ORDER BY created_at DESC LIMIT 20",
        uid);

    String[] keywords = extractKeywords(question);
    for (var memory : memories) {
      String content = (String) memory.get("content");
      for (String keyword : keywords) {
        if (content.contains(keyword)) {
          knowledge.add(content);
          break;
        }
      }
    }

    // Add user's current medications as context
    var meds = jdbc.queryForList(
        "SELECT name, notes FROM medications WHERE user_id=? AND enabled=1", uid);
    for (var med : meds) {
      String name = (String) med.get("name");
      String notes = (String) med.get("notes");
      if (!name.isBlank()) {
        knowledge.add("当前用药：" + name + (notes != null && !notes.isBlank() ? "，" + notes : ""));
      }
    }

    // Add user's active concerns from latest monitor data
    var latest = jdbc.queryForList(
        "SELECT hr, sleep_score, stress_score FROM monitor_records ORDER BY recorded_at DESC LIMIT 1");
    if (!latest.isEmpty()) {
      var r = latest.get(0);
      int hr = r.get("hr") instanceof Number n ? n.intValue() : 0;
      int sleep = r.get("sleep_score") instanceof Number n ? n.intValue() : 0;
      int stress = r.get("stress_score") instanceof Number n ? n.intValue() : 0;
      if (hr > 90) knowledge.add("当前心率偏高：" + hr + " bpm");
      if (sleep < 70) knowledge.add("当前睡眠评分偏低：" + sleep + " 分");
      if (stress > 65) knowledge.add("当前压力指数偏高：" + stress);
    }

    return knowledge.stream().distinct().limit(5).toList();
  }

  private String[] extractKeywords(String text) {
    return Arrays.stream(text.split("[，。？！、\\s,.?!]+"))
        .map(String::trim)
        .filter(w -> w.length() >= 2)
        .toArray(String[]::new);
  }
}
