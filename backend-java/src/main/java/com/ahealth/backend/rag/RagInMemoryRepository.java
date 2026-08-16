package com.ahealth.backend.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * 内存版 RAG 仓储：Redis Stack 不可用时的 fallback。
 *
 * <p>特点：
 * <ul>
 *   <li>纯内存，无持久化</li>
 *   <li>向量检索用 cosine 相似度暴力计算</li>
 *   <li>BM25 退化为关键词包含匹配</li>
 *   <li>适用于开发/测试环境，不建议生产使用</li>
 * </ul>
 */
@Component
public class RagInMemoryRepository implements RagRepository {

  private final Map<String, InMemoryDoc> store = new ConcurrentHashMap<>();
  private final AtomicInteger idCounter = new AtomicInteger(0);

  @Override
  public String upsert(RagDtos.RagDocument doc, float[] embedding) {
    String id = "mem_" + idCounter.incrementAndGet();
    store.put(id, new InMemoryDoc(doc, embedding));
    return id;
  }

  @Override
  public void delete(String redisDocId) {
    store.remove(redisDocId);
  }

  @Override
  public void deleteByDocType(String docType) {
    if (docType == null || docType.isBlank()) return;
    store.entrySet().removeIf(e -> docType.equals(e.getValue().doc().docType()));
  }

  @Override
  public List<RagDtos.RagSearchHit> searchByVector(float[] queryVector, String docType, int topK) {
    List<RagDtos.RagSearchHit> hits = new ArrayList<>();
    for (Map.Entry<String, InMemoryDoc> e : store.entrySet()) {
      InMemoryDoc d = e.getValue();
      if (docType != null && !docType.isBlank() && !docType.equals(d.doc.docType())) continue;
      double score = cosine(queryVector, d.embedding);
      hits.add(new RagDtos.RagSearchHit(
          e.getKey(),
          d.doc.docType(),
          d.doc.title(),
          d.doc.chunkText(),
          score,
          d.doc.metadata() != null ? d.doc.metadata() : Map.of()));
    }
    hits.sort(Comparator.comparingDouble(RagDtos.RagSearchHit::score).reversed());
    return hits.size() > topK ? hits.subList(0, topK) : hits;
  }

  @Override
  public List<RagDtos.RagSearchHit> hybridSearch(String queryText, float[] queryVector, String docType, int topK) {
    // 简化版：纯向量检索 + 关键词 boost
    List<RagDtos.RagSearchHit> hits = searchByVector(queryVector, docType, topK * 2);
    String lowerQuery = queryText == null ? "" : queryText.toLowerCase();
    List<RagDtos.RagSearchHit> boosted = new ArrayList<>();
    for (RagDtos.RagSearchHit h : hits) {
      double score = h.score();
      if (h.chunkText() != null && !lowerQuery.isEmpty()) {
        // 关键词命中加 0.1
        for (String token : lowerQuery.split("\\s+")) {
          if (token.length() > 1 && h.chunkText().toLowerCase().contains(token)) {
            score += 0.1;
          }
        }
      }
      boosted.add(new RagDtos.RagSearchHit(
          h.redisDocId(), h.docType(), h.title(), h.chunkText(), score, h.metadata()));
    }
    boosted.sort(Comparator.comparingDouble(RagDtos.RagSearchHit::score).reversed());
    return boosted.size() > topK ? boosted.subList(0, topK) : boosted;
  }

  @Override
  public boolean isAvailable() {
    return true; // 内存版总是可用
  }

  @Override
  public String repositoryType() {
    return "in-memory";
  }

  /** 计算两个向量的余弦相似度。 */
  private static double cosine(float[] a, float[] b) {
    if (a == null || b == null || a.length != b.length || a.length == 0) return 0.0;
    double dot = 0, na = 0, nb = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      na += a[i] * a[i];
      nb += b[i] * b[i];
    }
    if (na == 0 || nb == 0) return 0.0;
    return dot / (Math.sqrt(na) * Math.sqrt(nb));
  }

  private record InMemoryDoc(RagDtos.RagDocument doc, float[] embedding) {}
}
