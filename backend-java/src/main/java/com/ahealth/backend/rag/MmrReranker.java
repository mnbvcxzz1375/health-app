package com.ahealth.backend.rag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * MMR（Maximal Marginal Relevance）重排器：在 relevance 排序基础上，惩罚与已选文档相似的文档，提升多样性。
 *
 * <p>用于 RAG 检索后处理：避免 top-K 结果来自同 sourceId 或语义高度重叠的 chunks，提升答案覆盖面。
 *
 * <p>算法：
 * <pre>
 * selected[0] = candidates[0]  // 第一个选 relevance 最高
 * for k = 1..topK-1:
 *   selected[k] = argmax_i [ lambda * rel(i) - (1-lambda) * max_{j in selected} sim(i, j) ]
 * </pre>
 *
 * <p>参数：
 * <ul>
 *   <li>{@code lambda} ∈ [0, 1]，越大越偏相关性，越小越偏多样性（默认 0.7）</li>
 * </ul>
 *
 * <p>降级：MMR 计算失败 try-catch 返回 candidates 前 topK 条，不阻塞主流程。
 * 性能：仅当 candidates.size() > topK 时触发；每次需对 candidates 做 embed + 余弦计算，
 * query embedding 已被 {@link EmbeddingService#embed(String)} 缓存（30min TTL）。
 */
@Service
public class MmrReranker {

  private static final Logger log = LoggerFactory.getLogger(MmrReranker.class);

  private final EmbeddingService embeddingService;

  public MmrReranker(EmbeddingService embeddingService) {
    this.embeddingService = embeddingService;
  }

  /**
   * MMR 重排：返回 topK 条兼顾相关性与多样性的结果。
   *
   * @param query      原始查询（用于计算 query-candidate 相关性）
   * @param candidates 候选列表（已按 relevance 降序排列）
   * @param topK       返回条数
   * @param lambda     0.0-1.0，越大越偏相关性，越小越偏多样性
   * @return 重排后的 topK 条；若 candidates.size() ≤ topK 则原样返回
   */
  public List<RagDtos.RagSearchHit> rerank(
      String query, List<RagDtos.RagSearchHit> candidates, int topK, double lambda) {
    if (candidates == null || candidates.size() <= topK) {
      return candidates;
    }
    if (topK <= 0) {
      return List.of();
    }
    try {
      // 1. 计算 query 向量（享受 EmbeddingService 30min 缓存）
      float[] qvec = embeddingService.embed(query);
      if (qvec == null || qvec.length == 0) {
        return candidates.subList(0, topK);
      }

      // 2. 预计算每个 candidate 的 embedding（一次计算，多次复用）
      int n = candidates.size();
      float[][] candEmbs = new float[n][];
      boolean embOk = true;
      for (int i = 0; i < n; i++) {
        try {
          candEmbs[i] = embeddingService.embed(candidates.get(i).chunkText());
        } catch (Exception e) {
          log.debug("[MMR] candidate[{}] embed 失败，跳过 MMR: {}", i, e.getMessage());
          embOk = false;
          break;
        }
        if (candEmbs[i] == null || candEmbs[i].length == 0) {
          embOk = false;
          break;
        }
      }
      if (!embOk) {
        return candidates.subList(0, Math.min(topK, n));
      }

      // 3. 预计算每个 candidate 与 query 的相关性（cosine）
      double[] rel = new double[n];
      for (int i = 0; i < n; i++) {
        rel[i] = cosine(qvec, candEmbs[i]);
      }

      // 4. MMR 选择
      List<RagDtos.RagSearchHit> selected = new ArrayList<>(topK);
      Set<Integer> selectedIdx = new HashSet<>();

      // 第一个选 relevance 最高
      int firstIdx = 0;
      double firstRel = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < n; i++) {
        if (rel[i] > firstRel) {
          firstRel = rel[i];
          firstIdx = i;
        }
      }
      selected.add(candidates.get(firstIdx));
      selectedIdx.add(firstIdx);

      // 之后每轮选 max(lambda*rel - (1-lambda)*maxSim)
      while (selected.size() < topK) {
        int bestIdx = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
          if (selectedIdx.contains(i)) continue;
          // 计算 candidate[i] 与所有已选 candidate 的最大相似度
          double maxSim = 0.0;
          for (int j : selectedIdx) {
            double sim = cosine(candEmbs[i], candEmbs[j]);
            if (sim > maxSim) maxSim = sim;
          }
          double mmr = lambda * rel[i] - (1 - lambda) * maxSim;
          if (mmr > bestScore) {
            bestScore = mmr;
            bestIdx = i;
          }
        }
        if (bestIdx < 0) break;
        selected.add(candidates.get(bestIdx));
        selectedIdx.add(bestIdx);
      }

      log.debug("[MMR] rerank done: candidates={} topK={} lambda={} selected={}",
          n, topK, lambda, selected.size());
      return selected;
    } catch (Exception e) {
      log.warn("[MMR] rerank 异常，fallback 到前 topK 条: {}", e.getMessage());
      return candidates.subList(0, Math.min(topK, candidates.size()));
    }
  }

  /** 余弦相似度，向量自动归一化。 */
  private static double cosine(float[] a, float[] b) {
    if (a == null || b == null || a.length != b.length || a.length == 0) return 0.0;
    double dot = 0, normA = 0, normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    if (normA == 0 || normB == 0) return 0.0;
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }
}
