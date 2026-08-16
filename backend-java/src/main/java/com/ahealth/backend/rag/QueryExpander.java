package com.ahealth.backend.rag;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 查询扩展服务：对用户查询做同义词替换、术语归一化，提升召回率。
 *
 * <p>词典来源（启动时合并）：
 * <ul>
 *   <li>静态内置词典：常见药品商品名 ↔ 通用名、缩写展开、口语化归一</li>
 *   <li>DB 动态词典：tcm_herbs.alias（中药别名）、drug_clinical_info.ingredients（药品成分）</li>
 * </ul>
 *
 * <p>DB 加载失败仅打 WARN 日志，不阻止启动（fallback 到静态词典）。
 */
@Service
public class QueryExpander {

  private static final Logger log = LoggerFactory.getLogger(QueryExpander.class);

  /** 静态内置词典（启动时复制到实例字段，作为 DB 加载失败的兜底）。 */
  private static final Map<String, String> STATIC_SYNONYMS = new HashMap<>();
  private static final Map<String, String> STATIC_ABBREVIATIONS = new HashMap<>();

  static {
    // 药品商品名/通用名
    STATIC_SYNONYMS.put("阿司匹林", "ASA aspirin 乙酰水杨酸");
    STATIC_SYNONYMS.put("华法林", "warfarin 苄丙酮香豆素钠");
    STATIC_SYNONYMS.put("布洛芬", "ibuprofen 异丁苯丙酸");
    STATIC_SYNONYMS.put("对乙酰氨基酚", "paracetamol 扑热息痛");
    STATIC_SYNONYMS.put("二甲双胍", "metformin 格华止");
    STATIC_SYNONYMS.put("阿莫西林", "amoxicillin 羟氨苄青霉素");

    // 缩写
    STATIC_ABBREVIATIONS.put("BMI", "身体质量指数");
    STATIC_ABBREVIATIONS.put("BP", "血压");
    STATIC_ABBREVIATIONS.put("HR", "心率");
    STATIC_ABBREVIATIONS.put("DDI", "药物相互作用");
    STATIC_ABBREVIATIONS.put("TDM", "治疗药物监测");

    // 口语化归一
    STATIC_SYNONYMS.put("睡不着", "失眠 sleep");
    STATIC_SYNONYMS.put("头疼", "头痛 headache");
    STATIC_SYNONYMS.put("拉肚子", "腹泻 diarrhea");
    STATIC_SYNONYMS.put("感冒", "上呼吸道感染 URI");
  }

  private final JdbcTemplate jdbcTemplate;

  /** 运行时词典（静态 + DB 合并），查询时实际使用。 */
  private final Map<String, String> synonyms = new HashMap<>();
  private final Map<String, String> abbreviations = new HashMap<>();

  public QueryExpander(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * 启动时加载词典：先复制静态词典，再从 DB 加载动态别名合并。
   *
   * <p>DB 加载失败不抛异常，仅打 WARN 日志，QueryExpander 仍可用静态词典。
   */
  @PostConstruct
  public void init() {
    synonyms.putAll(STATIC_SYNONYMS);
    abbreviations.putAll(STATIC_ABBREVIATIONS);

    int tcmLoaded = 0;
    int drugLoaded = 0;
    try {
      tcmLoaded = loadTcmHerbAliases();
    } catch (Exception e) {
      log.warn("[QueryExpander] 加载 tcm_herbs.alias 失败: {}", e.getMessage());
    }
    try {
      drugLoaded = loadDrugIngredients();
    } catch (Exception e) {
      log.warn("[QueryExpander] 加载 drug_clinical_info.ingredients 失败: {}", e.getMessage());
    }
    log.info("[QueryExpander] 词典就绪：synonyms={}（静态 {} + DB {}）, abbreviations={}",
        synonyms.size(), STATIC_SYNONYMS.size(), tcmLoaded + drugLoaded, abbreviations.size());
  }

  /**
   * 从 tcm_herbs 表加载 name → alias 映射。
   *
   * <p>alias 字段可能是逗号/顿号/分号分隔的多个别名，统一拼接到 value 中。
   * 例如：name="甘草", alias="甜根子,蜜草" → synonyms.put("甘草", "甜根子 蜜草")
   *
   * @return 新增的别名条目数
   */
  private int loadTcmHerbAliases() {
    int loaded = 0;
    var rows = jdbcTemplate.queryForList(
        "SELECT name, alias FROM tcm_herbs WHERE alias IS NOT NULL AND alias <> '' AND name IS NOT NULL");
    for (var row : rows) {
      String name = String.valueOf(row.get("name")).trim();
      String alias = String.valueOf(row.get("alias")).trim();
      if (name.isEmpty() || alias.isEmpty()) continue;
      // 分隔符统一为空格（RediSearch BM25 分词友好）
      String normalized = alias.replaceAll("[,，;；、/|]+", " ").trim();
      if (normalized.isEmpty()) continue;
      // 不覆盖静态词典（静态优先级高）
      synonyms.putIfAbsent(name, normalized);
      loaded++;
    }
    return loaded;
  }

  /**
   * 从 drug_clinical_info 表加载 drug_name → ingredients 映射。
   *
   * <p>ingredients 字段通常含有效成分名（如"阿莫西林三水合物"），可作为药品名的扩展。
   * 例如：drug_name="阿莫西林胶囊", ingredients="阿莫西林三水合物" → synonyms.put("阿莫西林胶囊", "阿莫西林三水合物")
   *
   * @return 新增的别名条目数
   */
  private int loadDrugIngredients() {
    int loaded = 0;
    var rows = jdbcTemplate.queryForList(
        "SELECT drug_name, ingredients FROM drug_clinical_info "
            + "WHERE ingredients IS NOT NULL AND ingredients <> '' AND drug_name IS NOT NULL");
    for (var row : rows) {
      String drugName = String.valueOf(row.get("drug_name")).trim();
      String ingredients = String.valueOf(row.get("ingredients")).trim();
      if (drugName.isEmpty() || ingredients.isEmpty()) continue;
      // ingredients 可能很长（含辅料等），截断到 80 字符避免污染查询
      String truncated = ingredients.length() > 80 ? ingredients.substring(0, 80) : ingredients;
      synonyms.putIfAbsent(drugName, truncated);
      loaded++;
    }
    return loaded;
  }

  /**
   * 扩展查询：拼接同义词和缩写展开，保留原文优先。
   * 例如 "阿司匹林和华法林能一起吃吗" → "阿司匹林和华法林能一起吃吗 ASA aspirin 乙酰水杨酸 warfarin 苄丙酮香豆素钠"
   */
  public String expand(String query) {
    if (query == null || query.isBlank()) {
      return query;
    }
    StringBuilder expanded = new StringBuilder(query);
    for (Map.Entry<String, String> e : synonyms.entrySet()) {
      if (query.contains(e.getKey())) {
        expanded.append(" ").append(e.getValue());
      }
    }
    for (Map.Entry<String, String> e : abbreviations.entrySet()) {
      // 大小写敏感地匹配缩写（避免 bp 误匹配 blood pressure 中的 bp）
      if (query.contains(e.getKey())) {
        expanded.append(" ").append(e.getValue());
      }
    }
    return expanded.toString();
  }
}
