package com.ahealth.backend.rag;

import com.ahealth.backend.ai.DashScopeService;
import com.ahealth.backend.ai.LlmCacheService;
import com.ahealth.backend.ai.PromptTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * LLM 重排器：用大模型对 top-N 候选片段批量评分，返回 top-K。
 *
 * <p>设计要点：
 * <ul>
 *   <li>单次评分最多 20 个片段，避免超出 token 限制</li>
 *   <li>每个 chunk 截断到 200 字，控制输入规模</li>
 *   <li>LLM 调用失败时 fallback 到原 candidates 前 topK（保证可用性）</li>
 *   <li>System prompt 从 PromptTemplateService 加载（rag.rerank_system）</li>
 *   <li>Step 28 新增：LLM 缓存（scene=rag_rerank，TTL 7 天，相同 query+chunks 直接复用评分结果）</li>
 * </ul>
 */
@Service
public class Reranker {

  private static final Logger log = LoggerFactory.getLogger(Reranker.class);
  private static final int MAX_CHUNKS_PER_CALL = 20;
  private static final int CHUNK_TRUNCATE_LEN = 200;
  private static final Duration CACHE_TTL = Duration.ofHours(168); // 7 天

  private final DashScopeService dashScopeService;
  private final ObjectMapper objectMapper;
  private final PromptTemplateService promptTemplateService;
  private final LlmCacheService llmCacheService;

  public Reranker(
      DashScopeService dashScopeService,
      ObjectMapper objectMapper,
      PromptTemplateService promptTemplateService,
      LlmCacheService llmCacheService
  ) {
    this.dashScopeService = dashScopeService;
    this.objectMapper = objectMapper;
    this.promptTemplateService = promptTemplateService;
    this.llmCacheService = llmCacheService;
  }

  /**
   * 用 LLM 对 candidates 重排，返回 top-K 最相关片段。
   *
   * @param query      原始查询（未扩展）
   * @param candidates 候选片段列表（通常来自 hybridSearch top-20）
   * @param topK       返回条数
   * @return 重排后的 top-K 片段
   */
  public List<RagDtos.RagSearchHit> rerank(String query, List<RagDtos.RagSearchHit> candidates, int topK) {
    if (candidates == null || candidates.isEmpty()) return List.of();
    if (candidates.size() <= topK) return candidates;
    if (topK <= 0) return List.of();

    // 截断到 MAX_CHUNKS_PER_CALL 个
    List<RagDtos.RagSearchHit> subset = candidates.size() > MAX_CHUNKS_PER_CALL
        ? candidates.subList(0, MAX_CHUNKS_PER_CALL)
        : candidates;

    // === Layer 1: LLM 缓存（相同 query+chunks 直接复用评分结果） ===
    String contextHash = buildContextHash(query, subset);
    String promptKey = buildPromptKey(query, subset);
    Optional<String> cached = tryGetCache(promptKey, contextHash);
    if (cached.isPresent()) {
      List<RagDtos.RagSearchHit> fromCache = parseCachedScores(cached.get(), subset, topK);
      if (fromCache != null) {
        log.info("[Reranker] 缓存命中: query='{}' chunks={} topK={}",
            truncate(query, 50), subset.size(), topK);
        return fromCache;
      }
    }

    // === Layer 2: 调 LLM 评分 ===
    String systemPrompt = promptTemplateService.render("rag.rerank_system", Map.of());
    String userContent = buildUserContent(query, subset);

    try {
      JsonNode response = dashScopeService.requestJson(
          systemPrompt, userContent, dashScopeService.chatModel(), 0.0, "Rerank");

      // 期望返回 JSON 数组 [{"id":1,"score":8.5,"reason":"..."}]
      if (response == null || !response.isArray() || response.isEmpty()) {
        log.warn("[Reranker] LLM 返回非数组，fallback 到原顺序前 {} 条", topK);
        return candidates.subList(0, Math.min(topK, candidates.size()));
      }

      // 解析 id → score 映射
      Map<Integer, Double> scoreMap = new LinkedHashMap<>();
      for (JsonNode item : response) {
        int id = item.path("id").asInt(0);
        double score = item.path("score").asDouble(0.0);
        if (id > 0) scoreMap.put(id, score);
      }

      // 构造带新分数的列表
      List<RagDtos.RagSearchHit> scored = new ArrayList<>();
      for (int i = 0; i < subset.size(); i++) {
        RagDtos.RagSearchHit hit = subset.get(i);
        double newScore = scoreMap.getOrDefault(i + 1, hit.score());
        scored.add(new RagDtos.RagSearchHit(
            hit.redisDocId(), hit.docType(), hit.title(),
            hit.chunkText(), newScore, hit.metadata()));
      }

      // 按 score 降序，取 top-K
      scored.sort(Comparator.comparingDouble(RagDtos.RagSearchHit::score).reversed());
      List<RagDtos.RagSearchHit> result = scored.size() > topK ? scored.subList(0, topK) : scored;

      // === Layer 3: 写缓存（仅缓存 scoreMap，便于复用） ===
      tryWriteCache(promptKey, contextHash, response, subset.size());

      return result;
    } catch (Exception e) {
      log.warn("[Reranker] LLM 重排失败，fallback 到原顺序前 {} 条: {}", topK, e.getMessage());
      return candidates.subList(0, Math.min(topK, candidates.size()));
    }
  }

  /** 查询 rerank 缓存（失败仅 debug，不阻断主流程）。 */
  private Optional<String> tryGetCache(String promptKey, String contextHash) {
    try {
      return llmCacheService.getExact("rag_rerank", promptKey, contextHash);
    } catch (Exception e) {
      log.debug("[Reranker] 缓存查询失败: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /** 写入 rerank 缓存（失败仅 debug，不阻断主流程）。 */
  private void tryWriteCache(String promptKey, String contextHash, JsonNode response, int chunkCount) {
    try {
      llmCacheService.put("rag_rerank", promptKey, contextHash,
          objectMapper.writeValueAsString(response), CACHE_TTL, false);
      log.debug("[Reranker] 缓存写入: chunks={}", chunkCount);
    } catch (Exception e) {
      log.debug("[Reranker] 缓存写入失败: {}", e.getMessage());
    }
  }

  /**
   * 从缓存的 JSON 解析评分结果，构造 top-K 列表。
   *
   * @return 解析失败返回 null（让调用方 fallback 到 LLM）
   */
  private List<RagDtos.RagSearchHit> parseCachedScores(
      String cachedJson, List<RagDtos.RagSearchHit> subset, int topK) {
    try {
      JsonNode response = objectMapper.readTree(cachedJson);
      if (response == null || !response.isArray() || response.isEmpty()) return null;

      Map<Integer, Double> scoreMap = new LinkedHashMap<>();
      for (JsonNode item : response) {
        int id = item.path("id").asInt(0);
        double score = item.path("score").asDouble(0.0);
        if (id > 0) scoreMap.put(id, score);
      }

      List<RagDtos.RagSearchHit> scored = new ArrayList<>();
      for (int i = 0; i < subset.size(); i++) {
        RagDtos.RagSearchHit hit = subset.get(i);
        double newScore = scoreMap.getOrDefault(i + 1, hit.score());
        scored.add(new RagDtos.RagSearchHit(
            hit.redisDocId(), hit.docType(), hit.title(),
            hit.chunkText(), newScore, hit.metadata()));
      }
      scored.sort(Comparator.comparingDouble(RagDtos.RagSearchHit::score).reversed());
      return scored.size() > topK ? scored.subList(0, topK) : scored;
    } catch (Exception e) {
      log.debug("[Reranker] 缓存解析失败: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 构造 promptKey：query 截断 + chunks 数量（便于语义缓存时相似查询命中）。
   */
  private String buildPromptKey(String query, List<RagDtos.RagSearchHit> subset) {
    String q = query == null ? "" : query;
    String truncated = q.length() > 200 ? q.substring(0, 200) : q;
    return "rerank|q=" + truncated + "|n=" + subset.size();
  }

  /**
   * 构造 contextHash：SHA-256(query + 所有 chunkText 拼接)，保证 chunks 内容变化时缓存失效。
   */
  private String buildContextHash(String query, List<RagDtos.RagSearchHit> subset) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      StringBuilder sb = new StringBuilder(query == null ? "" : query);
      sb.append("|n=").append(subset.size()).append("|");
      for (RagDtos.RagSearchHit hit : subset) {
        sb.append(hit.redisDocId()).append(":");
        sb.append(hit.chunkText() == null ? "" : hit.chunkText()).append("\n");
      }
      byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      return "fallback-" + subset.hashCode();
    }
  }

  /** 构造 LLM 输入：{"query":"...","chunks":[{"id":1,"text":"..."},...]} */
  private String buildUserContent(String query, List<RagDtos.RagSearchHit> subset) {
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("query", query == null ? "" : query);
      List<Map<String, Object>> chunks = new ArrayList<>();
      for (int i = 0; i < subset.size(); i++) {
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", i + 1);
        chunk.put("text", truncate(subset.get(i).chunkText(), CHUNK_TRUNCATE_LEN));
        chunks.add(chunk);
      }
      body.put("chunks", chunks);
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      // 序列化失败时返回简化版
      return "query=" + query + "&chunks=" + subset.size();
    }
  }

  private String truncate(String text, int maxLen) {
    if (text == null) return "";
    return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
  }
}
