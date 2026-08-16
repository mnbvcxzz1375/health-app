package com.ahealth.backend.rag;

import java.util.List;

/**
 * RAG 仓储接口：抽象向量库的写入与检索。
 *
 * <p>实现：
 * <ul>
 *   <li>{@link RagRedisRepository} — Redis Stack 实现（生产路径）</li>
 *   <li>{@link RagInMemoryRepository} — 内存实现（Redis 不可用时的 fallback）</li>
 * </ul>
 *
 * <p>选择策略：通过 Spring Profile 或运行时探测 Redis 可用性自动切换。
 */
public interface RagRepository {

  /** 写入/更新一个文档，返回 Redis 文档 ID。 */
  String upsert(RagDtos.RagDocument doc, float[] embedding);

  /** 删除指定文档。 */
  void delete(String redisDocId);

  /** 按文档类型批量删除（默认空实现，由具体实现覆盖）。 */
  default void deleteByDocType(String docType) {}

  /** 纯向量检索：返回 top-K 命中。 */
  List<RagDtos.RagSearchHit> searchByVector(float[] queryVector, String docType, int topK);

  /** 混合检索：向量 top-K + BM25 top-K 融合去重。 */
  List<RagDtos.RagSearchHit> hybridSearch(String queryText, float[] queryVector, String docType, int topK);

  /** 按向量查询（用于 LLM 缓存语义命中）。返回 top-1 命中或空。 */
  default List<RagDtos.RagSearchHit> searchByVector(float[] queryVector, String docType) {
    return searchByVector(queryVector, docType, 1);
  }

  /** 探测仓储是否可用（Redis 连通性测试）。 */
  boolean isAvailable();

  /** 获取当前仓储类型名称（用于日志/监控）。 */
  String repositoryType();
}
