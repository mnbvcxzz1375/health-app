package com.ahealth.backend.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * RAG 摄入服务：把 MySQL 9 张表数据批量切片、向量化并写入 Redis 向量库。
 *
 * <p>覆盖的文档类型：
 * <ul>
 *   <li>consult_qa — 保留文档类型但默认跳过用户私有问诊记录，避免跨用户泄漏</li>
 *   <li>herb_guide — 中药材指南（tcm_herbs）</li>
 *   <li>drug_label — 药品说明书（drug_clinical_info）</li>
 *   <li>rehab_guide — 康复动作库（rehab_exercises）</li>
 *   <li>food_guide — 食物营养库（food_items）</li>
 *   <li>ddi_rule — 药物相互作用规则（ddi_knowledge）</li>
 *   <li>tcm_incompat — 中药配伍禁忌（tcm_incompatibility，Step 22 新增）</li>
 *   <li>tcm_wm — 中西药交互（tcm_wm_interaction，Step 22 新增）</li>
 *   <li>drug_food — 药物-食物交互（drug_food_interaction，Step 22 新增）</li>
 * </ul>
 *
 * <p>降级策略：Embedding 失败时 failed++，继续下一条；不阻塞整体流程。
 */
@Service
public class RagIngestionService {

  private static final Logger log = LoggerFactory.getLogger(RagIngestionService.class);

  private final JdbcTemplate jdbc;
  private final ChunkingService chunkingService;
  private final EmbeddingService embeddingService;
  private final RagRepository ragRepository;
  private final ObjectMapper objectMapper;

  public RagIngestionService(
      JdbcTemplate jdbc,
      ChunkingService chunkingService,
      EmbeddingService embeddingService,
      RagRepository ragRepository,
      ObjectMapper objectMapper
  ) {
    this.jdbc = jdbc;
    this.chunkingService = chunkingService;
    this.embeddingService = embeddingService;
    this.ragRepository = ragRepository;
    this.objectMapper = objectMapper;
  }

  /** 全量摄入所有 9 个数据源。 */
  public List<RagDtos.IngestResult> ingestAll() {
    List<RagDtos.IngestResult> results = new ArrayList<>();
    results.add(ingestConsultHistory());
    results.add(ingestHerbGuides());
    results.add(ingestDrugLabels());
    results.add(ingestRehabExercises());
    results.add(ingestFoodItems());
    results.add(ingestDdiKnowledge());
    results.add(ingestTcmIncompatibility());
    results.add(ingestTcmWmInteraction());
    results.add(ingestDrugFoodInteraction());
    return results;
  }

  /** 按文档类型摄入：先清旧，再调对应 ingest 方法。 */
  public RagDtos.IngestResult ingestByType(String docType) {
    if (docType == null || docType.isBlank()) {
      return new RagDtos.IngestResult("", 0, 0, "docType 为空");
    }
    ragRepository.deleteByDocType(docType);
    return switch (docType) {
      case "consult_qa" -> ingestConsultHistory();
      case "herb_guide" -> ingestHerbGuides();
      case "drug_label" -> ingestDrugLabels();
      case "rehab_guide" -> ingestRehabExercises();
      case "food_guide" -> ingestFoodItems();
      case "ddi_rule" -> ingestDdiKnowledge();
      case "tcm_incompat" -> ingestTcmIncompatibility();
      case "tcm_wm" -> ingestTcmWmInteraction();
      case "drug_food" -> ingestDrugFoodInteraction();
      default -> new RagDtos.IngestResult(docType, 0, 0, "未知 docType: " + docType);
    };
  }

  /** 1. 历史问诊记录 → consult_qa。 */
  public RagDtos.IngestResult ingestConsultHistory() {
    String docType = "consult_qa";
    // consult_history is user-scoped and may contain symptoms, medication and
    // other sensitive content. The global RAG index has no user filter, so it
    // must never ingest this table. A future anonymized/public QA table can be
    // added as a separate source with an explicit data-card contract.
    log.info("[RagIngestion] {} 跳过用户私有问诊记录，避免跨用户 RAG 泄漏", docType);
    return new RagDtos.IngestResult(docType, 0, 0, "已跳过用户私有问诊记录");
  }

  /**
   * 2. 中药材指南 → herb_guide。按字段结构化切片：pinyin / nature / flavor / meridian / efficacy
   * 各拆为独立 chunk，metadata 标注 field_name。
   */
  public RagDtos.IngestResult ingestHerbGuides() {
    String docType = "herb_guide";
    String table = "tcm_herbs";
    List<Map<String, Object>> rows;
    try {
      rows = jdbc.queryForList(
          "SELECT id, name, pinyin, nature, flavor, meridian, efficacy FROM tcm_herbs");
    } catch (Exception e) {
      log.warn("[RagIngestion] 读取 {} 失败: {}", table, e.getMessage());
      return new RagDtos.IngestResult(docType, 0, 0, "读取失败: " + e.getMessage());
    }
    int ingested = 0, failed = 0;
    for (Map<String, Object> row : rows) {
      Integer sourceId = intVal(row.get("id"));
      String name = str(row.get("name"));
      Map<String, String> fields = new LinkedHashMap<>();
      fields.put("pinyin", str(row.get("pinyin")));
      fields.put("nature", str(row.get("nature")));
      fields.put("flavor", str(row.get("flavor")));
      fields.put("meridian", str(row.get("meridian")));
      fields.put("efficacy", str(row.get("efficacy")));
      Map<String, String> baseMetadata = new LinkedHashMap<>();
      baseMetadata.put("herb_name", name);
      if (ingestStructuredRow(docType, table, sourceId, name, fields, baseMetadata)) {
        ingested++;
      } else {
        failed++;
      }
    }
    log.info("[RagIngestion] {} 摄入完成（结构化切片）: 成功={}, 失败={}", docType, ingested, failed);
    return new RagDtos.IngestResult(docType, ingested, failed, "OK");
  }

  /**
   * 3. 药品说明书 → drug_label。按字段结构化切片：ingredients / indications / contraindications / interactions
   * 各拆为独立 chunk，metadata 标注 field_name，便于未来检索时按字段过滤。
   */
  public RagDtos.IngestResult ingestDrugLabels() {
    String docType = "drug_label";
    String table = "drug_clinical_info";
    List<Map<String, Object>> rows;
    try {
      rows = jdbc.queryForList(
          "SELECT id, drug_name, ingredients, indications, contraindications, interactions "
          + "FROM drug_clinical_info");
    } catch (Exception e) {
      log.warn("[RagIngestion] 读取 {} 失败: {}", table, e.getMessage());
      return new RagDtos.IngestResult(docType, 0, 0, "读取失败: " + e.getMessage());
    }
    int ingested = 0, failed = 0;
    for (Map<String, Object> row : rows) {
      Integer sourceId = intVal(row.get("id"));
      String drugName = str(row.get("drug_name"));
      Map<String, String> fields = new LinkedHashMap<>();
      fields.put("ingredients", str(row.get("ingredients")));
      fields.put("indications", str(row.get("indications")));
      fields.put("contraindications", str(row.get("contraindications")));
      fields.put("interactions", str(row.get("interactions")));
      Map<String, String> baseMetadata = new LinkedHashMap<>();
      baseMetadata.put("drug_name", drugName);
      if (ingestStructuredRow(docType, table, sourceId, drugName, fields, baseMetadata)) {
        ingested++;
      } else {
        failed++;
      }
    }
    log.info("[RagIngestion] {} 摄入完成（结构化切片）: 成功={}, 失败={}", docType, ingested, failed);
    return new RagDtos.IngestResult(docType, ingested, failed, "OK");
  }

  /**
   * 4. 康复动作库 → rehab_guide。按字段结构化切片：category / duration / level / minutes / steps_json /
   * caution / focus / benefits_json / video_minutes 各拆为独立 chunk，metadata 标注 field_name +
   * exercise_name，便于检索时按字段过滤（如只查 caution 安全提示）。
   */
  public RagDtos.IngestResult ingestRehabExercises() {
    String docType = "rehab_guide";
    String table = "rehab_exercises";
    List<Map<String, Object>> rows;
    try {
      rows = jdbc.queryForList(
          "SELECT id, name, category, duration, level, minutes, steps_json, caution, focus, "
          + "benefits_json, video_minutes FROM rehab_exercises WHERE user_id IS NULL");
    } catch (Exception e) {
      log.warn("[RagIngestion] 读取 {} 失败: {}", table, e.getMessage());
      return new RagDtos.IngestResult(docType, 0, 0, "读取失败: " + e.getMessage());
    }
    int ingested = 0, failed = 0;
    for (Map<String, Object> row : rows) {
      Integer sourceId = intVal(row.get("id"));
      String name = str(row.get("name"));
      Map<String, String> fields = new LinkedHashMap<>();
      fields.put("category", str(row.get("category")));
      fields.put("duration", str(row.get("duration")));
      fields.put("level", str(row.get("level")));
      fields.put("minutes", str(row.get("minutes")));
      fields.put("steps_json", str(row.get("steps_json")));
      fields.put("caution", str(row.get("caution")));
      fields.put("focus", str(row.get("focus")));
      fields.put("benefits_json", str(row.get("benefits_json")));
      fields.put("video_minutes", str(row.get("video_minutes")));
      Map<String, String> baseMetadata = new LinkedHashMap<>();
      baseMetadata.put("exercise_name", name);
      if (ingestStructuredRow(docType, table, sourceId, name, fields, baseMetadata)) {
        ingested++;
      } else {
        failed++;
      }
    }
    log.info("[RagIngestion] {} 摄入完成（结构化切片）: 成功={}, 失败={}", docType, ingested, failed);
    return new RagDtos.IngestResult(docType, ingested, failed, "OK");
  }

  /**
   * 5. 食物营养库 → food_guide。按字段结构化切片：category / calories_per_100g / protein_g / fat_g / carb_g /
   * glycemic_index / tags 各拆为独立 chunk，metadata 标注 field_name。
   */
  public RagDtos.IngestResult ingestFoodItems() {
    String docType = "food_guide";
    String table = "food_items";
    List<Map<String, Object>> rows;
    try {
      rows = jdbc.queryForList(
          "SELECT id, name, category, calories_per_100g, protein_g, fat_g, carb_g, "
          + "glycemic_index, tags FROM food_items");
    } catch (Exception e) {
      log.warn("[RagIngestion] 读取 {} 失败: {}", table, e.getMessage());
      return new RagDtos.IngestResult(docType, 0, 0, "读取失败: " + e.getMessage());
    }
    int ingested = 0, failed = 0;
    for (Map<String, Object> row : rows) {
      Integer sourceId = intVal(row.get("id"));
      String name = str(row.get("name"));
      Map<String, String> fields = new LinkedHashMap<>();
      fields.put("category", str(row.get("category")));
      fields.put("calories_per_100g", str(row.get("calories_per_100g")));
      fields.put("protein_g", str(row.get("protein_g")));
      fields.put("fat_g", str(row.get("fat_g")));
      fields.put("carb_g", str(row.get("carb_g")));
      fields.put("glycemic_index", str(row.get("glycemic_index")));
      fields.put("tags", str(row.get("tags")));
      Map<String, String> baseMetadata = new LinkedHashMap<>();
      baseMetadata.put("food_name", name);
      if (ingestStructuredRow(docType, table, sourceId, name, fields, baseMetadata)) {
        ingested++;
      } else {
        failed++;
      }
    }
    log.info("[RagIngestion] {} 摄入完成（结构化切片）: 成功={}, 失败={}", docType, ingested, failed);
    return new RagDtos.IngestResult(docType, ingested, failed, "OK");
  }

  /** 6. 药物相互作用规则 → ddi_rule。 */
  public RagDtos.IngestResult ingestDdiKnowledge() {
    String docType = "ddi_rule";
    String table = "ddi_knowledge";
    List<Map<String, Object>> rows;
    try {
      rows = jdbc.queryForList(
          "SELECT id, drug_a, drug_b, severity, description, recommendation FROM ddi_knowledge");
    } catch (Exception e) {
      log.warn("[RagIngestion] 读取 {} 失败: {}", table, e.getMessage());
      return new RagDtos.IngestResult(docType, 0, 0, "读取失败: " + e.getMessage());
    }
    int ingested = 0, failed = 0;
    for (Map<String, Object> row : rows) {
      Integer sourceId = intVal(row.get("id"));
      String drugA = str(row.get("drug_a"));
      String drugB = str(row.get("drug_b"));
      String severity = str(row.get("severity"));
      String description = str(row.get("description"));
      String recommendation = str(row.get("recommendation"));
      StringBuilder sb = new StringBuilder();
      sb.append("药物相互作用：").append(drugA).append(" + ").append(drugB);
      sb.append("\n严重程度：").append(severity);
      if (!description.isBlank()) sb.append("\n描述：").append(description);
      if (!recommendation.isBlank()) sb.append("\n建议：").append(recommendation);
      String title = drugA + " + " + drugB;
      Map<String, String> metadata = new LinkedHashMap<>();
      metadata.put("drug_a", drugA);
      metadata.put("drug_b", drugB);
      metadata.put("severity", severity);
      if (ingestRow(docType, table, sourceId, title, sb.toString(), metadata)) {
        ingested++;
      } else {
        failed++;
      }
    }
    log.info("[RagIngestion] {} 摄入完成: 成功={}, 失败={}", docType, ingested, failed);
    return new RagDtos.IngestResult(docType, ingested, failed, "OK");
  }

  /**
   * 7. 中药配伍禁忌 → tcm_incompat（Step 22 新增）。
   * 整行切：herb_a + herb_b + type + description 内容连贯。
   */
  public RagDtos.IngestResult ingestTcmIncompatibility() {
    String docType = "tcm_incompat";
    String table = "tcm_incompatibility";
    List<Map<String, Object>> rows;
    try {
      rows = jdbc.queryForList(
          "SELECT id, herb_a, herb_b, type, description, source FROM tcm_incompatibility");
    } catch (Exception e) {
      log.warn("[RagIngestion] 读取 {} 失败: {}", table, e.getMessage());
      return new RagDtos.IngestResult(docType, 0, 0, "读取失败: " + e.getMessage());
    }
    int ingested = 0, failed = 0;
    for (Map<String, Object> row : rows) {
      Integer sourceId = intVal(row.get("id"));
      String herbA = str(row.get("herb_a"));
      String herbB = str(row.get("herb_b"));
      String type = str(row.get("type"));
      String description = str(row.get("description"));
      String source = str(row.get("source"));
      StringBuilder sb = new StringBuilder();
      sb.append("中药配伍禁忌：").append(herbA).append(" + ").append(herbB);
      sb.append("\n类型：").append(type);
      if (!description.isBlank()) sb.append("\n描述：").append(description);
      if (!source.isBlank()) sb.append("\n来源：").append(source);
      String title = herbA + " + " + herbB;
      Map<String, String> metadata = new LinkedHashMap<>();
      metadata.put("herb_a", herbA);
      metadata.put("herb_b", herbB);
      metadata.put("type", type);
      if (ingestRow(docType, table, sourceId, title, sb.toString(), metadata)) {
        ingested++;
      } else {
        failed++;
      }
    }
    log.info("[RagIngestion] {} 摄入完成: 成功={}, 失败={}", docType, ingested, failed);
    return new RagDtos.IngestResult(docType, ingested, failed, "OK");
  }

  /**
   * 8. 中西药交互 → tcm_wm（Step 22 新增）。
   * 整行切：tcm_name + wm_name + severity + interaction_type + description 内容连贯。
   */
  public RagDtos.IngestResult ingestTcmWmInteraction() {
    String docType = "tcm_wm";
    String table = "tcm_wm_interaction";
    List<Map<String, Object>> rows;
    try {
      rows = jdbc.queryForList(
          "SELECT id, tcm_name, wm_name, severity, interaction_type, "
          + "recommended_interval_minutes, description, evidence_source "
          + "FROM tcm_wm_interaction");
    } catch (Exception e) {
      log.warn("[RagIngestion] 读取 {} 失败: {}", table, e.getMessage());
      return new RagDtos.IngestResult(docType, 0, 0, "读取失败: " + e.getMessage());
    }
    int ingested = 0, failed = 0;
    for (Map<String, Object> row : rows) {
      Integer sourceId = intVal(row.get("id"));
      String tcmName = str(row.get("tcm_name"));
      String wmName = str(row.get("wm_name"));
      String severity = str(row.get("severity"));
      String interactionType = str(row.get("interaction_type"));
      String recommendedInterval = str(row.get("recommended_interval_minutes"));
      String description = str(row.get("description"));
      String evidenceSource = str(row.get("evidence_source"));
      StringBuilder sb = new StringBuilder();
      sb.append("中西药交互：").append(tcmName).append(" + ").append(wmName);
      sb.append("\n严重程度：").append(severity);
      if (!interactionType.isBlank()) sb.append("\n交互类型：").append(interactionType);
      if (!recommendedInterval.isBlank()) {
        sb.append("\n建议间隔（分钟）：").append(recommendedInterval);
      }
      if (!description.isBlank()) sb.append("\n描述：").append(description);
      if (!evidenceSource.isBlank()) sb.append("\n证据来源：").append(evidenceSource);
      String title = tcmName + " + " + wmName;
      Map<String, String> metadata = new LinkedHashMap<>();
      metadata.put("tcm_name", tcmName);
      metadata.put("wm_name", wmName);
      metadata.put("severity", severity);
      if (!interactionType.isBlank()) metadata.put("interaction_type", interactionType);
      if (ingestRow(docType, table, sourceId, title, sb.toString(), metadata)) {
        ingested++;
      } else {
        failed++;
      }
    }
    log.info("[RagIngestion] {} 摄入完成: 成功={}, 失败={}", docType, ingested, failed);
    return new RagDtos.IngestResult(docType, ingested, failed, "OK");
  }

  /**
   * 9. 药物-食物交互 → drug_food（Step 22 新增）。
   * 整行切：drug_name + food_category + food_items + severity + description 内容连贯。
   */
  public RagDtos.IngestResult ingestDrugFoodInteraction() {
    String docType = "drug_food";
    String table = "drug_food_interaction";
    List<Map<String, Object>> rows;
    try {
      rows = jdbc.queryForList(
          "SELECT id, drug_name, food_category, food_items, severity, description, source "
          + "FROM drug_food_interaction");
    } catch (Exception e) {
      log.warn("[RagIngestion] 读取 {} 失败: {}", table, e.getMessage());
      return new RagDtos.IngestResult(docType, 0, 0, "读取失败: " + e.getMessage());
    }
    int ingested = 0, failed = 0;
    for (Map<String, Object> row : rows) {
      Integer sourceId = intVal(row.get("id"));
      String drugName = str(row.get("drug_name"));
      String foodCategory = str(row.get("food_category"));
      String foodItems = str(row.get("food_items"));
      String severity = str(row.get("severity"));
      String description = str(row.get("description"));
      String source = str(row.get("source"));
      StringBuilder sb = new StringBuilder();
      sb.append("药物-食物交互：").append(drugName).append(" + ").append(foodCategory);
      sb.append("\n严重程度：").append(severity);
      if (!foodItems.isBlank()) sb.append("\n相关食物：").append(foodItems);
      if (!description.isBlank()) sb.append("\n描述：").append(description);
      if (!source.isBlank()) sb.append("\n来源：").append(source);
      String title = drugName + " + " + foodCategory;
      Map<String, String> metadata = new LinkedHashMap<>();
      metadata.put("drug_name", drugName);
      metadata.put("food_category", foodCategory);
      metadata.put("severity", severity);
      if (!foodItems.isBlank()) metadata.put("food_items", foodItems);
      if (ingestRow(docType, table, sourceId, title, sb.toString(), metadata)) {
        ingested++;
      } else {
        failed++;
      }
    }
    log.info("[RagIngestion] {} 摄入完成: 成功={}, 失败={}", docType, ingested, failed);
    return new RagDtos.IngestResult(docType, ingested, failed, "OK");
  }

  /**
   * 摄入单行：切片 → 向量化 → upsert。
   * @return true=成功 false=失败（已记日志）
   */
  private boolean ingestRow(String docType, String sourceTable, Integer sourceId,
      String title, String text, Map<String, String> metadata) {
    try {
      List<String> chunks = chunkingService.chunk(text);
      if (chunks.isEmpty()) {
        return true; // 空文本不摄入，但不算失败
      }
      List<float[]> embeddings = embeddingService.embedBatch(chunks);
      if (embeddings.size() != chunks.size()) {
        log.warn("[RagIngestion] embedding 数量不匹配: chunks={}, embeddings={}",
            chunks.size(), embeddings.size());
        return false;
      }
      for (int i = 0; i < chunks.size(); i++) {
        String chunkText = chunks.get(i);
        float[] emb = embeddings.get(i);
        int tokenCount = chunkingService.estimateTokens(chunkText);
        RagDtos.RagDocument doc = new RagDtos.RagDocument(
            docType, sourceTable, sourceId, title, chunkText, i, tokenCount, metadata);
        ragRepository.upsert(doc, emb);
      }
      return true;
    } catch (Exception e) {
      log.warn("[RagIngestion] 摄入失败 docType={} sourceId={}: {}",
          docType, sourceId, e.getMessage());
      return false;
    }
  }

  /**
   * 按字段结构化摄入：对每个字段单独切片 + 向量化 + upsert。
   *
   * <p>用于结构化数据源（drug_clinical_info / tcm_herbs / food_items）：
   * 一行数据按字段拆成多个 chunks，每个 chunk 的 metadata 标注 {@code field_name}，
   * metadata 会随 chunk 持久化，供证据展示、审计和后续字段级路由使用；当前公共检索入口按
   * {@code docType} 过滤，字段 metadata 仍作为返回证据的一部分保留。
   *
   * <p>示例：drug_clinical_info 一行 4 字段 → 4 个 chunks，每个 chunk 的 metadata 含
   * {@code field_name=ingredients} / {@code =indications} / {@code =contraindications} / {@code =interactions}。
   *
   * @param docType       文档类型
   * @param sourceTable   来源表名
   * @param sourceId      来源记录 ID
   * @param title         标题（通常是药物/药材/食物名称）
   * @param fields        字段名 → 字段值（顺序保留，使用 LinkedHashMap）
   * @param baseMetadata  基础 metadata（每条 chunk 都会带上，如 drug_name / category）
   * @return true=全部字段成功 false=至少一个字段失败
   */
  private boolean ingestStructuredRow(String docType, String sourceTable, Integer sourceId,
      String title, Map<String, String> fields, Map<String, String> baseMetadata) {
    boolean allOk = true;
    int chunkIndex = 0;
    for (Map.Entry<String, String> entry : fields.entrySet()) {
      String fieldName = entry.getKey();
      String fieldValue = entry.getValue();
      if (fieldValue == null || fieldValue.isBlank()) {
        continue; // 空字段跳过，不算失败
      }
      String fieldText = fieldName + ": " + fieldValue;
      try {
        List<String> chunks = chunkingService.chunk(fieldText);
        if (chunks.isEmpty()) {
          continue;
        }
        List<float[]> embeddings = embeddingService.embedBatch(chunks);
        if (embeddings.size() != chunks.size()) {
          log.warn("[RagIngestion] 结构化字段 embedding 数量不匹配: docType={} sourceId={} field={} chunks={} embs={}",
              docType, sourceId, fieldName, chunks.size(), embeddings.size());
          allOk = false;
          continue;
        }
        for (int i = 0; i < chunks.size(); i++) {
          String chunkText = chunks.get(i);
          float[] emb = embeddings.get(i);
          int tokenCount = chunkingService.estimateTokens(chunkText);
          // 合并 metadata：base + field_name
          Map<String, String> metadata = new LinkedHashMap<>();
          if (baseMetadata != null) metadata.putAll(baseMetadata);
          metadata.put("field_name", fieldName);
          RagDtos.RagDocument doc = new RagDtos.RagDocument(
              docType, sourceTable, sourceId, title, chunkText, chunkIndex, tokenCount, metadata);
          ragRepository.upsert(doc, emb);
          chunkIndex++;
        }
      } catch (Exception e) {
        log.warn("[RagIngestion] 结构化字段摄入失败 docType={} sourceId={} field={}: {}",
            docType, sourceId, fieldName, e.getMessage());
        allOk = false;
      }
    }
    return allOk;
  }

  private static String str(Object value) {
    if (value == null) return "";
    String s = String.valueOf(value).trim();
    return "null".equalsIgnoreCase(s) ? "" : s;
  }

  private static Integer intVal(Object value) {
    if (value == null) return null;
    if (value instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(String.valueOf(value).trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String truncate(String text, int maxLen) {
    if (text == null) return "";
    return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
  }
}
