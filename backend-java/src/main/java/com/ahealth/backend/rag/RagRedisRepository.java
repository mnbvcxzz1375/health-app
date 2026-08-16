package com.ahealth.backend.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

/**
 * Redis Stack 实现：用 RediSearch FT.SEARCH 完成 KNN 向量检索与 BM25 混合检索。
 *
 * <p>设计要点：
 * <ul>
 *   <li>通过 {@link RagRedisConfig#isRedisAvailable()} 探测，失败时由 RagInMemoryRepository 兜底</li>
 *   <li>embedding 字段使用 FLOAT32 little-endian 二进制存储（RediSearch 要求）</li>
 *   <li>upsert 同时写入 Redis Hash 与 MySQL rag_documents 元信息表</li>
 *   <li>hybridSearch = 向量 top-20 + BM25 top-20 加权融合（向量 0.7 + BM25 0.3）</li>
 * </ul>
 */
@Component
@Primary
public class RagRedisRepository implements RagRepository {

  private static final Logger log = LoggerFactory.getLogger(RagRedisRepository.class);

  private final JedisPooled jedisPooled;
  private final RagRedisConfig ragRedisConfig;
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  @Value("${rag.redis.index-name:ahealth_rag}")
  private String indexName;

  @Value("${rag.redis.prefix:rag:doc:}")
  private String keyPrefix;

  public RagRedisRepository(
      JedisPooled jedisPooled,
      RagRedisConfig ragRedisConfig,
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper
  ) {
    this.jedisPooled = jedisPooled;
    this.ragRedisConfig = ragRedisConfig;
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean isAvailable() {
    return ragRedisConfig.isRedisAvailable();
  }

  @Override
  public String repositoryType() {
    return "redis-stack";
  }

  @Override
  public String upsert(RagDtos.RagDocument doc, float[] embedding) {
    // 用确定性 id 实现幂等：同 source 的同 chunk 重复摄入时覆盖而非新增
    String deterministicId = buildDeterministicId(doc);
    String redisDocId = keyPrefix + deterministicId;
    Map<String, String> hashFields = new HashMap<>();
    hashFields.put("doc_type", nullSafe(doc.docType()));
    hashFields.put("source_table", nullSafe(doc.sourceTable()));
    hashFields.put("source_id", doc.sourceId() == null ? "" : String.valueOf(doc.sourceId()));
    hashFields.put("title", nullSafe(doc.title()));
    hashFields.put("chunk_text", nullSafe(doc.chunkText()));
    hashFields.put("chunk_index", String.valueOf(doc.chunkIndex()));
    hashFields.put("token_count", String.valueOf(doc.tokenCount()));
    hashFields.put("embedding", bytesToHex(embeddingToBytes(embedding)));

    // metadata 序列化到单独字段（用 _meta 前缀避免与 schema 字段冲突）
    if (doc.metadata() != null && !doc.metadata().isEmpty()) {
      try {
        hashFields.put("_meta_json", objectMapper.writeValueAsString(doc.metadata()));
      } catch (Exception ignored) {
      }
    }

    try {
      jedisPooled.hset(redisDocId, hashFields);
    } catch (Exception e) {
      log.warn("[RagRedis] upsert HSET 失败 redisDocId={}: {}", redisDocId, e.getMessage());
      throw new RuntimeException("Redis upsert 失败: " + e.getMessage(), e);
    }

    // 同步写入 MySQL rag_documents 元信息（UPSERT 保证幂等）
    try {
      jdbcTemplate.update(
          """
          INSERT INTO rag_documents (doc_type, source_table, source_id, title, chunk_text, chunk_index, token_count, redis_doc_id, metadata_json)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          ON DUPLICATE KEY UPDATE
            title = VALUES(title),
            chunk_text = VALUES(chunk_text),
            token_count = VALUES(token_count),
            metadata_json = VALUES(metadata_json)
          """,
          doc.docType(),
          doc.sourceTable(),
          doc.sourceId(),
          doc.title(),
          doc.chunkText(),
          doc.chunkIndex(),
          doc.tokenCount(),
          redisDocId,
          doc.metadata() == null ? null : objectMapper.writeValueAsString(doc.metadata())
      );
    } catch (Exception e) {
      log.debug("[RagRedis] rag_documents 写入失败（不影响 Redis）: {}", e.getMessage());
    }

    return redisDocId;
  }

  /** 构造确定性 redisDocId 片段（不含 keyPrefix），保证同源同 chunk 幂等。 */
  private String buildDeterministicId(RagDtos.RagDocument doc) {
    String docType = nullSafe(doc.docType());
    String sourceTable = nullSafe(doc.sourceTable());
    String sourceId = doc.sourceId() == null ? "" : String.valueOf(doc.sourceId());
    int chunkIndex = doc.chunkIndex();
    // 字段名（如有）加入 id 以区分结构化切片的不同字段 chunk
    String fieldName = doc.metadata() == null ? "" : doc.metadata().getOrDefault("field_name", "");
    String fieldPart = fieldName.isEmpty() ? "" : ":" + sanitize(fieldName);
    return sanitize(docType) + ":" + sanitize(sourceTable) + ":" + sanitize(sourceId) + ":" + chunkIndex + fieldPart;
  }

  /** 清理 id 段中的非法字符（避免破坏 Redis key 结构）。 */
  private String sanitize(String s) {
    return s == null ? "" : s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
  }

  @Override
  public void delete(String redisDocId) {
    try {
      jedisPooled.del(redisDocId);
    } catch (Exception e) {
      log.warn("[RagRedis] delete DEL 失败 redisDocId={}: {}", redisDocId, e.getMessage());
    }
    try {
      jdbcTemplate.update("DELETE FROM rag_documents WHERE redis_doc_id=?", redisDocId);
    } catch (Exception ignored) {
    }
  }

  @Override
  public void deleteByDocType(String docType) {
    if (docType == null || docType.isBlank()) return;
    try {
      // 先查所有匹配 docType 的文档 id
      Query query = new Query("@doc_type:{" + escapeTag(docType) + "}")
          .limit(0, 10000)
          .returnFields("doc_type");
      SearchResult result = jedisPooled.ftSearch(indexName, query);
      for (redis.clients.jedis.search.Document doc : result.getDocuments()) {
        jedisPooled.del(doc.getId());
      }
    } catch (Exception e) {
      log.warn("[RagRedis] deleteByDocType 失败 docType={}: {}", docType, e.getMessage());
    }
    try {
      jdbcTemplate.update("DELETE FROM rag_documents WHERE doc_type=?", docType);
    } catch (Exception ignored) {
    }
  }

  @Override
  public List<RagDtos.RagSearchHit> searchByVector(float[] queryVector, String docType, int topK) {
    if (!isAvailable()) return List.of();
    String blob = bytesToHex(embeddingToBytes(queryVector));

    // KNN 查询：*=>[KNN $K @embedding $BLOB]
    // 或带过滤：(@doc_type:{xxx})=>[KNN $K @embedding $BLOB]
    String queryStr;
    Map<String, Object> params = new HashMap<>();
    params.put("K", String.valueOf(topK));
    params.put("BLOB", blob);

    if (docType != null && !docType.isBlank()) {
      queryStr = "(@doc_type:{" + escapeTag(docType) + "})=>[KNN $K @embedding $BLOB]";
    } else {
      queryStr = "*=>[KNN $K @embedding $BLOB]";
    }

    try {
      Query query = new Query(queryStr)
          .addParam("K", String.valueOf(topK))
          .addParam("BLOB", blob)
          .limit(0, topK)
          .returnFields("doc_type", "title", "chunk_text", "_meta_json", "__embedding_score");
      SearchResult result = jedisPooled.ftSearch(indexName, query);
      return parseSearchResult(result, true);
    } catch (Exception e) {
      log.warn("[RagRedis] searchByVector 失败: {}", e.getMessage());
      return List.of();
    }
  }

  @Override
  public List<RagDtos.RagSearchHit> hybridSearch(String queryText, float[] queryVector, String docType, int topK) {
    if (!isAvailable()) return List.of();

    // 向量检索 top-20
    List<RagDtos.RagSearchHit> vectorHits = searchByVector(queryVector, docType, Math.max(topK * 2, 20));

    // BM25 检索 top-20
    List<RagDtos.RagSearchHit> bm25Hits = bm25Search(queryText, docType, Math.max(topK * 2, 20));

    // 合并去重 + 加权融合
    Map<String, RagDtos.RagSearchHit> merged = new LinkedHashMap<>();
    Map<String, Double> scores = new HashMap<>();

    for (RagDtos.RagSearchHit h : vectorHits) {
      merged.put(h.redisDocId(), h);
      scores.put(h.redisDocId(), h.score() * 0.7);
    }
    for (RagDtos.RagSearchHit h : bm25Hits) {
      double bm25Normalized = Math.min(1.0, h.score() / 10.0);
      scores.merge(h.redisDocId(), bm25Normalized * 0.3, Double::sum);
      merged.putIfAbsent(h.redisDocId(), h);
    }

    List<RagDtos.RagSearchHit> fused = new ArrayList<>();
    for (Map.Entry<String, RagDtos.RagSearchHit> e : merged.entrySet()) {
      RagDtos.RagSearchHit original = e.getValue();
      double fusedScore = scores.getOrDefault(e.getKey(), 0.0);
      fused.add(new RagDtos.RagSearchHit(
          original.redisDocId(), original.docType(), original.title(),
          original.chunkText(), fusedScore, original.metadata()));
    }
    fused.sort(Comparator.comparingDouble(RagDtos.RagSearchHit::score).reversed());
    return fused.size() > topK ? fused.subList(0, topK) : fused;
  }

  /** BM25 检索：对 chunk_text 做分词后查询。 */
  private List<RagDtos.RagSearchHit> bm25Search(String queryText, String docType, int topK) {
    if (queryText == null || queryText.isBlank()) return List.of();

    // 中文分词简化：按空格/标点切分，构造 OR 查询
    String[] tokens = queryText.split("[\\s，。！？、,.?!;:：]+");
    StringBuilder queryBuilder = new StringBuilder();
    for (int i = 0; i < tokens.length; i++) {
      if (tokens[i].length() < 1) continue;
      if (queryBuilder.length() > 0) queryBuilder.append("|");
      queryBuilder.append(escapeQueryToken(tokens[i]));
    }
    if (queryBuilder.length() == 0) return List.of();

    String queryStr = "@chunk_text:(" + queryBuilder + ")";
    if (docType != null && !docType.isBlank()) {
      queryStr = "(@doc_type:{" + escapeTag(docType) + "} " + queryStr + ")";
    }

    try {
      Query query = new Query(queryStr)
          .limit(0, topK)
          .returnFields("doc_type", "title", "chunk_text", "_meta_json");
      SearchResult result = jedisPooled.ftSearch(indexName, query);
      return parseSearchResult(result, false);
    } catch (Exception e) {
      log.warn("[RagRedis] bm25Search 失败: {}", e.getMessage());
      return List.of();
    }
  }

  /** 解析 RediSearch SearchResult 为 RagSearchHit 列表。 */
  private List<RagDtos.RagSearchHit> parseSearchResult(SearchResult result, boolean isVectorSearch) {
    List<RagDtos.RagSearchHit> hits = new ArrayList<>();
    if (result == null || result.getDocuments() == null) return hits;

    for (redis.clients.jedis.search.Document doc : result.getDocuments()) {
      String redisDocId = doc.getId();
      String docType = strValue(doc.get("doc_type"));
      String title = strValue(doc.get("title"));
      String chunkText = strValue(doc.get("chunk_text"));
      String metaJson = strValue(doc.get("_meta_json"));
      Map<String, String> metadata = parseMetadata(metaJson);

      double score = 0.0;
      if (isVectorSearch) {
        // RediSearch KNN 返回 __<field>_score 字段
        String scoreStr = strValue(doc.get("__embedding_score"));
        if (scoreStr.isEmpty()) scoreStr = strValue(doc.get("embedding_score"));
        if (!scoreStr.isEmpty()) {
          try {
            score = Double.parseDouble(scoreStr);
          } catch (NumberFormatException ignored) {
          }
        }
      } else {
        // BM25 score
        score = doc.getScore();
      }

      hits.add(new RagDtos.RagSearchHit(redisDocId, docType, title, chunkText, score, metadata));
    }
    return hits;
  }

  // === 工具方法 ===

  private String nullSafe(String s) {
    return s == null ? "" : s;
  }

  private String strValue(Object obj) {
    return obj == null ? "" : String.valueOf(obj);
  }

  private Map<String, String> parseMetadata(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(json, Map.class);
    } catch (Exception e) {
      return Map.of();
    }
  }

  private String escapeTag(String tag) {
    return tag.replace(",", "\\,").replace("|", "\\|");
  }

  private String escapeQueryToken(String token) {
    return token.replaceAll("([@:\\\"\\(\\)\\[\\]\\{\\}\\*\\?])", "\\\\$1");
  }

  /** float[] → little-endian byte[]（RediSearch 要求 little-endian FLOAT32）。 */
  private byte[] embeddingToBytes(float[] embedding) {
    if (embedding == null || embedding.length == 0) return new byte[0];
    ByteBuffer buffer = ByteBuffer.allocate(embedding.length * 4).order(ByteOrder.LITTLE_ENDIAN);
    for (float f : embedding) buffer.putFloat(f);
    return buffer.array();
  }

  /** byte[] → hex string（RediSearch PARAMS 要求 hex 编码或 Base64，这里用 hex 简化）。 */
  private String bytesToHex(byte[] bytes) {
    if (bytes == null || bytes.length == 0) return "";
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b & 0xFF));
    }
    return sb.toString();
  }
}
