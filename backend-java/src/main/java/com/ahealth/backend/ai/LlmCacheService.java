package com.ahealth.backend.ai;

import com.ahealth.backend.rag.EmbeddingService;
import com.ahealth.backend.rag.RagDtos;
import com.ahealth.backend.rag.RagRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * LLM 响应缓存：精确缓存（sha256）+ 语义缓存（向量检索 + 阈值匹配）双层。
 *
 * <p>精确缓存：sha256(scene + "|" + promptKey + "|" + contextHash) → MySQL llm_response_cache 表 + Caffeine 本地缓存
 *
 * <p>语义缓存：仅低温度（<0.4）场景启用。对 promptKey 做 embedding，从向量库
 * doc_type="llm_cache_{scene}" 中检索 top-1，相似度 ≥ 0.92 时返回。
 *
 * <p>降级策略：缓存读失败 → 返回 empty（透传到 LLM）；缓存写失败 → WARN 日志（不影响主流程）。
 */
@Service
public class LlmCacheService {

  private static final Logger log = LoggerFactory.getLogger(LlmCacheService.class);
  private static final double SEMANTIC_THRESHOLD = 0.92;
  private static final List<String> KNOWN_SCENES = List.of(
      "consult", "explain_medication", "rehab_plan_draft", "upload_analysis", "rag_rerank");

  private final JdbcTemplate jdbc;
  private final EmbeddingService embeddingService;
  private final RagRepository ragRepository;
  private final ObjectMapper objectMapper;

  private final Cache<String, String> local = Caffeine.newBuilder()
      .expireAfterWrite(Duration.ofMinutes(5))
      .maximumSize(500)
      .build();

  public LlmCacheService(
      JdbcTemplate jdbc,
      EmbeddingService embeddingService,
      RagRepository ragRepository,
      ObjectMapper objectMapper
  ) {
    this.jdbc = jdbc;
    this.embeddingService = embeddingService;
    this.ragRepository = ragRepository;
    this.objectMapper = objectMapper;
  }

  /** 精确缓存查询：先查 Caffeine，未命中查 MySQL，命中回填 Caffeine 并 hit_count++。 */
  public Optional<String> getExact(String scene, String promptKey, String contextHash) {
    if (scene == null || promptKey == null) return Optional.empty();
    String ctxHash = contextHash == null ? "" : contextHash;
    String cacheKey = sha256(scene + "|" + promptKey + "|" + ctxHash);

    String localHit = local.getIfPresent(cacheKey);
    if (localHit != null) {
      return Optional.of(localHit);
    }

    try {
      Map<String, Object> row;
      try {
        row = jdbc.queryForMap(
            "SELECT response_text FROM llm_response_cache "
            + "WHERE cache_key=? AND (expires_at IS NULL OR expires_at>NOW())",
            cacheKey);
      } catch (org.springframework.dao.EmptyResultDataAccessException e) {
        return Optional.empty();
      }
      String response = (String) row.get("response_text");
      if (response == null) return Optional.empty();
      // 回填本地缓存
      local.put(cacheKey, response);
      // 异步更新 hit_count
      try {
        jdbc.update("UPDATE llm_response_cache SET hit_count=hit_count+1 WHERE cache_key=?", cacheKey);
      } catch (Exception ignored) {
        // hit_count 更新失败不影响主流程
      }
      return Optional.of(response);
    } catch (Exception e) {
      log.warn("[LlmCache] getExact 异常: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * 语义缓存查询：对 promptKey 做 embedding → 向量库 top-1 检索 → score ≥ 0.92 时返回。
   * 仅低温度（<0.4）场景启用，避免高创造性回答被错误复用。
   */
  public Optional<String> getSemantic(String scene, String promptKey) {
    if (scene == null || promptKey == null || promptKey.isBlank()) return Optional.empty();
    if (!embeddingService.isConfigured()) return Optional.empty();
    try {
      float[] emb = embeddingService.embed(promptKey);
      if (emb == null || emb.length == 0) return Optional.empty();
      List<RagDtos.RagSearchHit> hits = ragRepository.searchByVector(emb, "llm_cache_" + scene, 1);
      if (hits == null || hits.isEmpty()) return Optional.empty();
      RagDtos.RagSearchHit hit = hits.get(0);
      if (hit.score() < SEMANTIC_THRESHOLD) {
        log.debug("[LlmCache] 语义未命中: scene={} score={} threshold={}",
            scene, hit.score(), SEMANTIC_THRESHOLD);
        return Optional.empty();
      }
      Map<String, String> meta = hit.metadata();
      if (meta == null) return Optional.empty();
      String response = meta.get("response");
      if (response == null || response.isBlank()) return Optional.empty();
      log.info("[LlmCache] 语义命中: scene={} score={}", scene, hit.score());
      return Optional.of(response);
    } catch (Exception e) {
      log.warn("[LlmCache] getSemantic 异常: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * 写入缓存：精确缓存（MySQL + Caffeine），可选语义缓存（向量库）。
   *
   * @param scene        场景标识（consult / explain_medication / rehab_plan_draft ...）
   * @param promptKey    生成 cacheKey 的 prompt 标识（通常 systemPrompt + userContent 序列化）
   * @param contextHash  上下文哈希（用于区分不同用户上下文的相同 prompt）
   * @param response     LLM 返回的文本
   * @param ttl          过期时间
   * @param withSemantic 是否同时写入语义缓存
   */
  public void put(String scene, String promptKey, String contextHash,
      String response, Duration ttl, boolean withSemantic) {
    if (scene == null || promptKey == null || response == null || response.isBlank()) return;
    String ctxHash = contextHash == null ? "" : contextHash;
    String cacheKey = sha256(scene + "|" + promptKey + "|" + ctxHash);
    String promptHash = sha256(promptKey);

    // 本地缓存
    local.put(cacheKey, response);

    // MySQL 持久化（UPSERT）
    try {
      LocalDateTime expiresAt = (ttl == null || ttl.isZero() || ttl.isNegative())
          ? null : LocalDateTime.now().plus(ttl);
      jdbc.update(
          "INSERT INTO llm_response_cache(cache_key, scene, prompt_hash, prompt_text, response_text, expires_at, created_at) "
          + "VALUES(?,?,?,?,?,?,NOW()) "
          + "ON DUPLICATE KEY UPDATE prompt_text=VALUES(prompt_text), response_text=VALUES(response_text), "
          + "expires_at=VALUES(expires_at), hit_count=0",
          cacheKey, scene, promptHash, truncate(promptKey, 1000), response, expiresAt);
    } catch (Exception e) {
      log.warn("[LlmCache] put MySQL 异常: {}", e.getMessage());
    }

    // 语义缓存（写入向量库）
    if (withSemantic && embeddingService.isConfigured()) {
      try {
        float[] emb = embeddingService.embed(promptKey);
        if (emb != null && emb.length > 0) {
          Map<String, String> metadata = new HashMap<>();
          metadata.put("response", response);
          metadata.put("scene", scene);
          metadata.put("cache_key", cacheKey);
          RagDtos.RagDocument doc = new RagDtos.RagDocument(
              "llm_cache_" + scene, "llm_response_cache", null,
              truncate(promptKey, 100), promptKey, 0, 0, metadata);
          ragRepository.upsert(doc, emb);
        }
      } catch (Exception e) {
        log.warn("[LlmCache] put 语义缓存异常: {}", e.getMessage());
      }
    }
  }

  /** 按场景清理：删 MySQL 行 + 删向量库 doc_type=llm_cache_{scene} 的所有文档。 */
  public void evict(String scene) {
    if (scene == null || scene.isBlank()) return;
    try {
      jdbc.update("DELETE FROM llm_response_cache WHERE scene=?", scene);
    } catch (Exception e) {
      log.warn("[LlmCache] evict MySQL 异常: {}", e.getMessage());
    }
    try {
      ragRepository.deleteByDocType("llm_cache_" + scene);
    } catch (Exception e) {
      log.warn("[LlmCache] evict 向量库异常: {}", e.getMessage());
    }
    // 清理本地缓存中该 scene 的所有 key（无法精准按 scene 过滤，直接 invalidateAll）
    local.invalidateAll();
  }

  /** 全量清空。 */
  public void evictAll() {
    try {
      jdbc.update("DELETE FROM llm_response_cache");
    } catch (Exception e) {
      log.warn("[LlmCache] evictAll MySQL 异常: {}", e.getMessage());
    }
    for (String scene : KNOWN_SCENES) {
      try {
        ragRepository.deleteByDocType("llm_cache_" + scene);
      } catch (Exception ignored) {
      }
    }
    local.invalidateAll();
  }

  /** 缓存统计：各 scene 的 hit_count 总和、条目数、最近 24h 命中率。 */
  public Map<String, Object> stats() {
    Map<String, Object> stats = new LinkedHashMap<>();
    try {
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT scene, COUNT(*) AS cnt, SUM(hit_count) AS total_hits "
          + "FROM llm_response_cache GROUP BY scene ORDER BY scene");
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
    stats.put("localSize", local.estimatedSize());
    stats.put("semanticThreshold", SEMANTIC_THRESHOLD);
    return stats;
  }

  /** 计算 SHA-256 哈希，返回 64 字符 hex 字符串。 */
  private static String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      // 退化为字符串 hashcode（不应发生，SHA-256 是标准算法）
      return Integer.toHexString(input.hashCode());
    }
  }

  private static String truncate(String text, int maxLen) {
    if (text == null) return "";
    return text.length() <= maxLen ? text : text.substring(0, maxLen);
  }
}
