package com.ahealth.backend.ai;

import com.ahealth.backend.rag.RagDtos;
import com.ahealth.backend.rag.RagSearchService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 答案模板服务：高频问题秒级响应，避免不必要的 LLM 调用。
 *
 * <p>三层降级路由：
 * <ol>
 *   <li>priority ≥ 50（高置信）：直接返回模板文本，0 LLM 调用、0 RAG 检索</li>
 *   <li>priority < 50（中置信）：模板含 {@code {{knowledge}}} 占位符时，走 RAG 检索填空（用
 *       {@link RagSearchService#searchWithoutRerank} 跳过 LLM 重排），0 LLM 调用</li>
 *   <li>未命中模板：返回 empty，由调用方走 RAG + LLM</li>
 * </ol>
 *
 * <p>匹配优先级：keywords 全包含 > pattern 正则匹配；同优先级按 priority DESC 排序。
 */
@Service
public class AnswerTemplateService {

  private static final Logger log = LoggerFactory.getLogger(AnswerTemplateService.class);
  private static final int HIGH_CONFIDENCE_THRESHOLD = 50;
  private static final String KNOWLEDGE_PLACEHOLDER = "{{knowledge}}";
  private static final String QUESTION_PLACEHOLDER = "{{question}}";

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final RagSearchService ragSearchService;

  // active 模板缓存（5min TTL，避免每次查询都查 DB）
  private final Cache<String, List<AnswerTemplate>> activeCache = Caffeine.newBuilder()
      .expireAfterWrite(Duration.ofMinutes(5))
      .maximumSize(50)
      .build();

  public AnswerTemplateService(
      JdbcTemplate jdbc, ObjectMapper objectMapper, RagSearchService ragSearchService) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.ragSearchService = ragSearchService;
  }

  /**
   * 三层降级入口：尝试用模板回答问题。
   *
   * @return 命中时返回渲染后的文本；未命中返回 empty
   */
  public Optional<String> tryAnswer(String question, String scene) {
    if (question == null || question.isBlank() || scene == null) return Optional.empty();
    try {
      Optional<MatchResult> match = match(question, scene);
      if (match.isEmpty()) return Optional.empty();

      MatchResult result = match.get();
      String rendered = result.renderedText();
      if (rendered == null || rendered.isBlank()) return Optional.empty();

      // 异步更新 hit_count（best-effort）
      try {
        jdbc.update("UPDATE answer_templates SET hit_count=hit_count+1 WHERE id=?",
            result.template().id());
      } catch (Exception ignored) {
        // hit_count 更新失败不影响主流程
      }

      log.info("[AnswerTemplate] 命中: scene={} key={} confidence={} priority={}",
          scene, result.template().templateKey(), result.confidence(),
          result.template().priority());
      return Optional.of(rendered);
    } catch (Exception e) {
      log.warn("[AnswerTemplate] tryAnswer 异常: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * 匹配模板：按 priority DESC 查 active 模板，keywords 全包含或 pattern 正则匹配。
   *
   * @return 命中时返回 MatchResult（含渲染后文本）；未命中返回 empty
   */
  public Optional<MatchResult> match(String question, String scene) {
    List<AnswerTemplate> templates = listActive(scene);
    if (templates.isEmpty()) return Optional.empty();

    String normalizedQ = question.trim().toLowerCase();
    for (AnswerTemplate t : templates) {
      boolean matched = false;
      String confidence = "low";

      // 优先 keywords 全包含匹配
      if (t.keywords() != null && !t.keywords().isEmpty()) {
        boolean allContains = t.keywords().stream()
            .map(String::trim)
            .map(String::toLowerCase)
            .allMatch(kw -> !kw.isEmpty() && normalizedQ.contains(kw));
        if (allContains) {
          matched = true;
          confidence = "high";
        }
      }

      // 其次 pattern 正则匹配
      if (!matched && t.pattern() != null && !t.pattern().isBlank()) {
        try {
          if (Pattern.matches(t.pattern(), question)) {
            matched = true;
            confidence = "medium";
          }
        } catch (Exception e) {
          // 正则编译失败忽略
        }
      }

      if (matched) {
        String rendered = renderTemplate(t, question, scene);
        return Optional.of(new MatchResult(t, rendered, confidence));
      }
    }
    return Optional.empty();
  }

  /**
   * 渲染模板：替换 {{question}} / {{knowledge}}。
   *
   * <p>当模板含 {@code {{knowledge}}} 且 priority < 50（中置信）时，调
   * {@link RagSearchService#searchWithoutRerank} 检索 top-3 知识填充（Step 27 集成，跳过 LLM 重排）。
   *
   * <p>当 priority ≥ 50（高置信）时，即使含 {@code {{knowledge}}} 也不查 RAG（0 调用），直接删除占位符。
   */
  public String renderTemplate(AnswerTemplate template, String question, String scene) {
    String text = template.templateText();
    if (text == null) return "";
    text = text.replace(QUESTION_PLACEHOLDER, question == null ? "" : question);

    if (text.contains(KNOWLEDGE_PLACEHOLDER)) {
      String knowledge = "";
      if (template.priority() < HIGH_CONFIDENCE_THRESHOLD) {
        knowledge = retrieveKnowledgeForTemplate(question, scene);
      }
      text = text.replace(KNOWLEDGE_PLACEHOLDER, knowledge);
    }
    return text;
  }

  /** 测试匹配：不更新 hit_count，用于 admin 端点验证。 */
  public Optional<MatchResult> testMatch(String question, String scene) {
    if (question == null || question.isBlank()) return Optional.empty();
    List<AnswerTemplate> templates = listActive(scene);
    String normalizedQ = question.trim().toLowerCase();
    for (AnswerTemplate t : templates) {
      boolean matched = false;
      if (t.keywords() != null && !t.keywords().isEmpty()) {
        if (t.keywords().stream()
            .map(String::trim).map(String::toLowerCase)
            .allMatch(kw -> !kw.isEmpty() && normalizedQ.contains(kw))) {
          matched = true;
        }
      }
      if (!matched && t.pattern() != null && !t.pattern().isBlank()) {
        try {
          if (Pattern.matches(t.pattern(), question)) matched = true;
        } catch (Exception ignored) {
        }
      }
      if (matched) {
        return Optional.of(new MatchResult(t, renderTemplate(t, question, scene), "test"));
      }
    }
    return Optional.empty();
  }

  /**
   * 用 searchWithoutRerank 检索知识（跳过 LLM 重排，节省 1 次 LLM 调用）。
   * 检索失败返回空字符串，不阻塞模板渲染。
   */
  private String retrieveKnowledgeForTemplate(String question, String scene) {
    if (question == null || question.isBlank()) return "";
    try {
      String docType = mapSceneToDocType(scene);
      List<RagDtos.RagSearchHit> hits = ragSearchService.searchWithoutRerank(question, docType, 3);
      if (hits == null || hits.isEmpty()) return "";
      StringBuilder sb = new StringBuilder();
      for (RagDtos.RagSearchHit hit : hits) {
        if (hit.chunkText() != null && !hit.chunkText().isBlank()) {
          sb.append("- ").append(hit.chunkText().trim()).append("\n");
        }
      }
      return sb.toString().trim();
    } catch (Exception e) {
      log.debug("[AnswerTemplate] RAG 检索失败，跳过 knowledge 填充: {}", e.getMessage());
      return "";
    }
  }

  /** scene → docType 映射（用于 RAG 检索时过滤文档类型）。 */
  private String mapSceneToDocType(String scene) {
    if (scene == null) return null;
    return switch (scene) {
      case "consult" -> null; // 全类型检索
      case "explain_medication" -> "drug_label";
      case "rehab_plan_draft" -> "rehab_guide";
      default -> null;
    };
  }

  /** 列出激活模板（带 Caffeine 缓存）。 */
  public List<AnswerTemplate> listActive(String scene) {
    if (scene == null) return List.of();
    String cacheKey = "active:" + scene;
    List<AnswerTemplate> cached = activeCache.getIfPresent(cacheKey);
    if (cached != null) return cached;

    List<AnswerTemplate> result = loadActiveFromDb(scene);
    activeCache.put(cacheKey, result);
    return result;
  }

  private List<AnswerTemplate> loadActiveFromDb(String scene) {
    try {
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT id, template_key, scene, category, keywords_json, pattern, "
          + "template_text, variables_json, priority, hit_count, version "
          + "FROM answer_templates WHERE is_active=1 AND scene=? ORDER BY priority DESC, id ASC",
          scene);
      return rows.stream().map(this::mapRow).toList();
    } catch (Exception e) {
      log.warn("[AnswerTemplate] 加载 active 模板失败: scene={} err={}", scene, e.getMessage());
      return List.of();
    }
  }

  @SuppressWarnings("unchecked")
  private AnswerTemplate mapRow(Map<String, Object> row) {
    int id = ((Number) row.get("id")).intValue();
    String templateKey = (String) row.get("template_key");
    String scene = (String) row.get("scene");
    String category = (String) row.get("category");
    List<String> keywords = parseJsonList((String) row.get("keywords_json"));
    String pattern = (String) row.get("pattern");
    String templateText = (String) row.get("template_text");
    List<String> variables = parseJsonList((String) row.get("variables_json"));
    int priority = ((Number) row.get("priority")).intValue();
    int hitCount = row.get("hit_count") == null ? 0 : ((Number) row.get("hit_count")).intValue();
    int version = ((Number) row.get("version")).intValue();
    return new AnswerTemplate(id, templateKey, scene, category, keywords, pattern,
        templateText, variables, priority, hitCount, version);
  }

  private List<String> parseJsonList(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }

  // === Admin CRUD ===

  /** 列出模板（支持按 scene / key 过滤）。 */
  public List<AnswerTemplate> list(String scene, String key) {
    StringBuilder sql = new StringBuilder(
        "SELECT id, template_key, scene, category, keywords_json, pattern, "
        + "template_text, variables_json, priority, hit_count, version "
        + "FROM answer_templates WHERE 1=1");
    List<Object> args = new ArrayList<>();
    if (scene != null && !scene.isBlank()) {
      sql.append(" AND scene=?");
      args.add(scene);
    }
    if (key != null && !key.isBlank()) {
      sql.append(" AND template_key=?");
      args.add(key);
    }
    sql.append(" ORDER BY template_key, version DESC");
    return jdbc.queryForList(sql.toString(), args.toArray()).stream()
        .map(this::mapRow).toList();
  }

  /** 查看某 template_key 的所有版本。 */
  public List<AnswerTemplate> listVersions(String templateKey) {
    return jdbc.queryForList(
        "SELECT id, template_key, scene, category, keywords_json, pattern, "
        + "template_text, variables_json, priority, hit_count, version "
        + "FROM answer_templates WHERE template_key=? ORDER BY version DESC",
        templateKey).stream().map(this::mapRow).toList();
  }

  /** upsert：若 template_key 存在则新建更高版本，否则插入 v1。返回新版本号。 */
  public int upsert(UpsertRequest req) {
    // 查当前最大版本号
    Integer maxVersion = null;
    try {
      maxVersion = jdbc.queryForObject(
          "SELECT MAX(version) FROM answer_templates WHERE template_key=?",
          Integer.class, req.templateKey());
    } catch (Exception ignored) {
    }
    int newVersion = maxVersion == null ? 1 : maxVersion + 1;

    jdbc.update(
        "INSERT INTO answer_templates(template_key, scene, category, keywords_json, pattern, "
        + "template_text, variables_json, priority, is_active, version) "
        + "VALUES(?,?,?,?,?,?,?,?,-1,?)",
        req.templateKey(), req.scene() == null ? "consult" : req.scene(),
        req.category(), toJson(req.keywords()),
        req.pattern(), req.templateText(), toJson(req.variables()),
        req.priority(), newVersion);

    // 自动激活新版本：先 deactivate 旧版本，再 activate 新版本
    jdbc.update("UPDATE answer_templates SET is_active=0 WHERE template_key=? AND version<>?",
        req.templateKey(), newVersion);
    jdbc.update("UPDATE answer_templates SET is_active=1 WHERE template_key=? AND version=?",
        req.templateKey(), newVersion);

    activeCache.invalidateAll();
    return newVersion;
  }

  /** 激活指定版本：先 deactivate 同 key 所有版本，再 activate 指定版本。 */
  public void activate(int id) {
    String templateKey = jdbc.queryForObject(
        "SELECT template_key FROM answer_templates WHERE id=?", String.class, id);
    if (templateKey == null) return;
    jdbc.update("UPDATE answer_templates SET is_active=0 WHERE template_key=?", templateKey);
    jdbc.update("UPDATE answer_templates SET is_active=1 WHERE id=?", id);
    activeCache.invalidateAll();
  }

  /** 删除指定版本。 */
  public void delete(int id) {
    jdbc.update("DELETE FROM answer_templates WHERE id=?", id);
    activeCache.invalidateAll();
  }

  /** 统计：各 scene 的模板数、总命中数。 */
  public Map<String, Object> stats() {
    Map<String, Object> stats = new LinkedHashMap<>();
    try {
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT scene, COUNT(*) AS cnt, SUM(hit_count) AS total_hits "
          + "FROM answer_templates GROUP BY scene ORDER BY scene");
      stats.put("byScene", rows);
      stats.put("totalEntries", rows.stream().mapToInt(r ->
          ((Number) r.getOrDefault("cnt", 0)).intValue()).sum());
      stats.put("totalHits", rows.stream().mapToLong(r -> {
        Object v = r.get("total_hits");
        return v == null ? 0L : ((Number) v).longValue();
      }).sum());
    } catch (Exception e) {
      stats.put("error", e.getMessage());
      stats.put("byScene", List.of());
      stats.put("totalEntries", 0);
      stats.put("totalHits", 0L);
    }
    stats.put("cacheSize", activeCache.estimatedSize());
    return stats;
  }

  private String toJson(List<String> list) {
    if (list == null || list.isEmpty()) return "[]";
    try {
      return objectMapper.writeValueAsString(list);
    } catch (Exception e) {
      return "[]";
    }
  }

  // === DTOs ===

  public record AnswerTemplate(
      int id,
      String templateKey,
      String scene,
      String category,
      List<String> keywords,
      String pattern,
      String templateText,
      List<String> variables,
      int priority,
      int hitCount,
      int version) {}

  public record MatchResult(AnswerTemplate template, String renderedText, String confidence) {}

  public record UpsertRequest(
      String templateKey,
      String scene,
      String category,
      List<String> keywords,
      String pattern,
      String templateText,
      List<String> variables,
      int priority) {}
}
