package com.ahealth.backend.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Prompt 模板服务：从 prompt_templates 表加载激活版本的模板，支持版本管理与热更新。
 *
 * <p>设计要点：
 * <ul>
 *   <li>Caffeine 5min 本地缓存，避免每次调用都查 DB</li>
 *   <li>DB 加载失败时 fallback 到内存默认模板（与原硬编码一致），保证可用性</li>
 *   <li>支持 {{var}} 占位符渲染</li>
 *   <li>upsert 自动递增版本号，旧版本置为 is_active=0</li>
 * </ul>
 */
@Service
public class PromptTemplateService {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final Cache<String, Optional<PromptTemplate>> cache;

  public PromptTemplateService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.cache = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(200)
        .build();
  }

  /** 获取指定 key 的激活版本模板。DB 异常时返回 null。 */
  public PromptTemplate get(String key) {
    return cache.get(key, k -> loadFromDb(k)).orElse(null);
  }

  /** 渲染模板：将 {{var}} 替换为 variables 中的值。模板不存在时返回 fallback 默认内容。 */
  public String render(String key, Map<String, Object> variables) {
    PromptTemplate t = get(key);
    String content = t != null ? t.content() : fallbackContent(key);
    if (variables == null || variables.isEmpty()) {
      return content;
    }
    for (Map.Entry<String, Object> e : variables.entrySet()) {
      content = content.replace("{{" + e.getKey() + "}}", String.valueOf(e.getValue()));
    }
    return content;
  }

  /** 创建新版本：递增 version，旧版本置为 is_active=0。返回新创建的模板。 */
  public PromptTemplate upsert(String key, String scene, String content, List<String> variables,
      String description) {
    // 1. 找到当前最大版本
    Integer maxVersion = jdbc.query(
        "SELECT MAX(version) FROM prompt_templates WHERE template_key=?",
        rs -> rs.next() ? (Integer) rs.getObject(1) : null,
        key);
    int newVersion = (maxVersion == null ? 0 : maxVersion) + 1;

    // 2. 旧版本全部置为 inactive
    jdbc.update("UPDATE prompt_templates SET is_active=0 WHERE template_key=?", key);

    // 3. 插入新版本
    String variablesJson = serializeVariables(variables);
    jdbc.update(
        """
        INSERT INTO prompt_templates (template_key, scene, content, variables_json, version, is_active, description)
        VALUES (?, ?, ?, ?, ?, 1, ?)
        """,
        key, scene, content, variablesJson, newVersion, description);

    // 4. 清缓存
    cache.invalidate(key);

    return loadFromDb(key).orElse(null);
  }

  /** 列出指定 key 的所有版本。 */
  public List<PromptTemplate> listVersions(String key) {
    return jdbc.query(
        """
        SELECT id, template_key, scene, content, variables_json, version, is_active, description, created_at
        FROM prompt_templates
        WHERE template_key=?
        ORDER BY version DESC
        """,
        (rs, i) -> mapRow(rs),
        key);
  }

  /** 按 key/scene 列出激活模板。 */
  public List<PromptTemplate> listActive(String key, String scene) {
    StringBuilder sql = new StringBuilder(
        "SELECT id, template_key, scene, content, variables_json, version, is_active, description, created_at "
            + "FROM prompt_templates WHERE is_active=1");
    List<Object> params = new ArrayList<>();
    if (key != null && !key.isBlank()) {
      sql.append(" AND template_key=?");
      params.add(key);
    }
    if (scene != null && !scene.isBlank()) {
      sql.append(" AND scene=?");
      params.add(scene);
    }
    sql.append(" ORDER BY template_key");
    return jdbc.query(sql.toString(), (rs, i) -> mapRow(rs), params.toArray());
  }

  /** 激活指定 id 的版本（同 key 其他版本置为 inactive）。 */
  public PromptTemplate activate(int id) {
    String key = jdbc.query(
        "SELECT template_key FROM prompt_templates WHERE id=?",
        rs -> rs.next() ? rs.getString(1) : null,
        id);
    if (key == null) {
      return null;
    }
    jdbc.update("UPDATE prompt_templates SET is_active=0 WHERE template_key=?", key);
    jdbc.update("UPDATE prompt_templates SET is_active=1 WHERE id=?", id);
    cache.invalidate(key);
    return loadFromDb(key).orElse(null);
  }

  /** 回滚到指定 id 的版本（即激活该版本）。语义上等价于 activate。 */
  public PromptTemplate rollback(int id) {
    return activate(id);
  }

  /** 失效指定 key 的缓存（用于 PromptTemplateController 在 upsert/activate 后强制刷新）。 */
  public void evictCache(String key) {
    cache.invalidate(key);
  }

  /** 失效所有缓存。 */
  public void evictAll() {
    cache.invalidateAll();
  }

  private Optional<PromptTemplate> loadFromDb(String key) {
    try {
      PromptTemplate t = jdbc.queryForObject(
          """
          SELECT id, template_key, scene, content, variables_json, version, is_active, description, created_at
          FROM prompt_templates
          WHERE template_key=? AND is_active=1
          """,
          (rs, i) -> mapRow(rs),
          key);
      return Optional.ofNullable(t);
    } catch (Exception e) {
      // DB 异常时不缓存失败结果，返回 empty 但不写入 cache
      return Optional.empty();
    }
  }

  private PromptTemplate mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new PromptTemplate(
        rs.getInt("id"),
        rs.getString("template_key"),
        rs.getString("scene"),
        rs.getString("content"),
        deserializeVariables(rs.getString("variables_json")),
        rs.getInt("version"),
        rs.getInt("is_active") == 1,
        rs.getString("description"),
        rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime()
            : null);
  }

  private String serializeVariables(List<String> variables) {
    if (variables == null || variables.isEmpty()) {
      return "[]";
    }
    try {
      return objectMapper.writeValueAsString(variables);
    } catch (Exception e) {
      return "[]";
    }
  }

  private List<String> deserializeVariables(String json) {
    if (json == null || json.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  /**
   * 兜底模板：当 prompt_templates 表为空或 DB 不可用时使用。
   * 内容与原硬编码 prompt 一致，保证服务可用性。
   */
  private String fallbackContent(String key) {
    return switch (key) {
      case "consult.assistant_system" -> """
          你是中文健康管理助手。
          你只能提供健康管理、监测解读、康复训练和就医建议的辅助说明，不能替代医生诊断与治疗。
          请只返回 JSON，不要输出 Markdown，不要输出代码块。
          固定结构为：
          {"answer":"","suggestions":["","",""],"disclaimer":""}
          """;
      case "consult.router_base" -> "你是中文健康管理助手，只提供健康管理辅助说明，不能替代医生诊断。";
      case "consult.router_medication" -> "你专注于用药安全和药物管理。回答时优先考虑药物相互作用、剂量安全和服药时间。";
      case "consult.router_blood_pressure" -> "你专注于血压管理。结合用户血压数据给出个性化建议。";
      case "consult.router_sleep" -> "你专注于睡眠健康。结合用户睡眠数据给出改善建议。";
      case "consult.router_exercise" -> "你专注于运动康复。结合用户活动数据给出安全的运动建议。";
      case "consult.router_general" -> "请根据用户健康数据给出 3 到 5 句清晰建议。";
      case "consult.agent_system" -> """
          你是中文健康管理助手，具备工具调用能力。
          你只能提供健康管理、监测解读、康复训练和就医建议的辅助说明，不能替代医生诊断与治疗。
          请只返回 JSON。
          """;
      case "medication.recognition_system" -> "你是药盒文字结构化提取助手。请只返回 JSON。";
      case "medication.recognition_user" -> "请对本次上传的全部图片一次性完成识别。";
      case "medication.explain_system" -> "你是药学助手。请根据药品名称，生成结构化药学解释。只返回 JSON。";
      case "upload.analysis_system" -> "你是中文健康资料分析助手。请只返回 JSON。";
      case "upload.rehab_plan_draft_system" -> "你是中文康复计划生成助手。请只输出 JSON。";
      case "herb_recognition.system" -> "你是中药材识别专家。请识别图片中所有可见的中药材，返回 JSON 数组。";
      case "rag.rerank_system" -> "你是中文医学检索重排助手。给每个片段打 0-10 分。只返回 JSON 数组 [{\"id\":1,\"score\":8.5,\"reason\":\"...\"}]。";
      default -> "";
    };
  }

  /** PromptTemplate 内部 record。 */
  public record PromptTemplate(
      int id,
      String templateKey,
      String scene,
      String content,
      List<String> variables,
      int version,
      boolean isActive,
      String description,
      java.time.LocalDateTime createdAt) {}
}
