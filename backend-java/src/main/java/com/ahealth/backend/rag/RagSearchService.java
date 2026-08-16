package com.ahealth.backend.rag;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * RAG 检索主入口：查询扩展 → 混合检索 → LLM 重排 → MMR 多样性后处理。
 *
 * <p>降级策略：所有 RAG 调用 try-catch，失败仅 WARN 日志返回空列表，不阻塞主流程。
 *
 * <p>检索流程：
 * <ol>
 *   <li>查询扩展：QueryExpander.expand(query) — 同义词/缩写归一</li>
 *   <li>向量化：EmbeddingService.embed(expandedQuery)</li>
 *   <li>混合检索：RagRepository.hybridSearch(expanded, vector, docType, 20) — 向量 0.7 + BM25 0.3</li>
 *   <li>LLM 重排：Reranker.rerank(originalQuery, candidates, topK*1.5) — top-20 → top-K*1.5</li>
 *   <li>MMR 后处理：MmrReranker.rerank(query, candidates, topK, lambda) — top-K*1.5 → top-K，提升多样性</li>
 * </ol>
 */
@Service
public class RagSearchService {

  private static final Logger log = LoggerFactory.getLogger(RagSearchService.class);
  private static final int CANDIDATE_TOP_K = 20;

  private final QueryExpander queryExpander;
  private final RagRepository ragRepository;
  private final EmbeddingService embeddingService;
  private final Reranker reranker;
  private final MmrReranker mmrReranker;
  private final double mmrLambda;

  public RagSearchService(
      QueryExpander queryExpander,
      RagRepository ragRepository,
      EmbeddingService embeddingService,
      Reranker reranker,
      MmrReranker mmrReranker,
      @Value("${rag.search.mmr-lambda:0.7}") double mmrLambda
  ) {
    this.queryExpander = queryExpander;
    this.ragRepository = ragRepository;
    this.embeddingService = embeddingService;
    this.reranker = reranker;
    this.mmrReranker = mmrReranker;
    this.mmrLambda = mmrLambda;
  }

  /**
   * 完整检索：查询扩展 + 混合检索 + LLM 重排 + MMR 后处理。
   *
   * @param query   原始查询文本
   * @param docType 文档类型过滤（null/空 = 全部）
   * @param topK    返回条数
   * @return 重排后的 top-K 命中片段；任何环节失败返回空列表
   */
  public List<RagDtos.RagSearchHit> search(String query, String docType, int topK) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    int effectiveTopK = topK <= 0 ? 5 : Math.min(topK, 20);
    String effectiveDocType = (docType == null || docType.isBlank()) ? null : docType.trim();

    try {
      String expanded = queryExpander.expand(query);
      log.debug("[RagSearch] query='{}' expanded='{}' docType={} topK={}",
          query, expanded, effectiveDocType, effectiveTopK);

      float[] qvec;
      try {
        qvec = embeddingService.embed(expanded);
      } catch (Exception e) {
        log.warn("[RagSearch] embedding 失败，返回空结果: {}", e.getMessage());
        return List.of();
      }
      if (qvec == null || qvec.length == 0) {
        return List.of();
      }

      List<RagDtos.RagSearchHit> candidates;
      try {
        candidates = ragRepository.hybridSearch(expanded, qvec, effectiveDocType, CANDIDATE_TOP_K);
      } catch (Exception e) {
        log.warn("[RagSearch] hybridSearch 失败，返回空结果: {}", e.getMessage());
        return List.of();
      }
      if (candidates == null || candidates.isEmpty()) {
        return List.of();
      }

      // 候选数 ≤ topK 时跳过重排
      if (candidates.size() <= effectiveTopK) {
        return candidates;
      }

      // LLM 重排：top-20 → top-K*1.5（保留稍多以供 MMR 选择）
      int rerankTopK = (int) Math.ceil(effectiveTopK * 1.5);
      List<RagDtos.RagSearchHit> reranked;
      try {
        reranked = reranker.rerank(query, candidates, Math.min(rerankTopK, candidates.size()));
      } catch (Exception e) {
        log.warn("[RagSearch] rerank 失败，fallback 到前 {} 条: {}", effectiveTopK, e.getMessage());
        reranked = candidates.subList(0, Math.min(effectiveTopK, candidates.size()));
      }

      // MMR 多样性后处理：top-K*1.5 → top-K
      try {
        List<RagDtos.RagSearchHit> mmrResult = mmrReranker.rerank(query, reranked, effectiveTopK, mmrLambda);
        if (mmrResult != null && !mmrResult.isEmpty()) {
          return mmrResult;
        }
      } catch (Exception e) {
        log.warn("[RagSearch] MMR 后处理失败，fallback 到 reranker 结果: {}", e.getMessage());
      }
      return reranked.subList(0, Math.min(effectiveTopK, reranked.size()));
    } catch (Exception e) {
      log.warn("[RagSearch] 检索异常: {}", e.getMessage(), e);
      return List.of();
    }
  }

  /**
   * 跳过 LLM 重排的检索（用于低延迟场景或缓存命中后），但仍走 MMR 后处理。
   */
  public List<RagDtos.RagSearchHit> searchWithoutRerank(String query, String docType, int topK) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    int effectiveTopK = topK <= 0 ? 5 : Math.min(topK, 20);
    String effectiveDocType = (docType == null || docType.isBlank()) ? null : docType.trim();

    try {
      String expanded = queryExpander.expand(query);
      float[] qvec = embeddingService.embed(expanded);
      if (qvec == null || qvec.length == 0) {
        return List.of();
      }
      // 取 topK*1.5 候选以供 MMR 选择
      int candidateTopK = Math.min((int) Math.ceil(effectiveTopK * 1.5), CANDIDATE_TOP_K);
      List<RagDtos.RagSearchHit> candidates = ragRepository.hybridSearch(
          expanded, qvec, effectiveDocType, Math.max(candidateTopK, effectiveTopK));
      if (candidates == null || candidates.isEmpty()) {
        return List.of();
      }
      if (candidates.size() <= effectiveTopK) {
        return candidates;
      }
      // MMR 后处理
      try {
        List<RagDtos.RagSearchHit> mmrResult = mmrReranker.rerank(query, candidates, effectiveTopK, mmrLambda);
        if (mmrResult != null && !mmrResult.isEmpty()) {
          return mmrResult;
        }
      } catch (Exception e) {
        log.warn("[RagSearch] searchWithoutRerank MMR 失败，fallback: {}", e.getMessage());
      }
      return candidates.subList(0, Math.min(effectiveTopK, candidates.size()));
    } catch (Exception e) {
      log.warn("[RagSearch] searchWithoutRerank 异常: {}", e.getMessage());
      return List.of();
    }
  }
}
