package com.ahealth.backend.rag;

import java.util.List;
import java.util.Map;

/** RAG 模块共用 DTOs。 */
public final class RagDtos {

  private RagDtos() {}

  /** 待摄入的 RAG 文档。 */
  public record RagDocument(
      String docType,            // consult_qa/herb_guide/drug_label/rehab_guide/food_guide/ddi_rule
      String sourceTable,        // 来源 MySQL 表
      Integer sourceId,          // 来源记录 ID
      String title,
      String chunkText,
      int chunkIndex,
      int tokenCount,
      Map<String, String> metadata) {}

  /** 检索命中结果。 */
  public record RagSearchHit(
      String redisDocId,
      String docType,
      String title,
      String chunkText,
      double score,              // 向量相似度或 BM25 归一化分数
      Map<String, String> metadata) {}

  /** 摄入任务结果。 */
  public record IngestResult(String docType, int ingested, int failed, String message) {}

  /** 搜索请求。 */
  public record SearchRequest(String query, String docType, int topK) {
    public SearchRequest {
      if (topK <= 0) topK = 5;
      if (topK > 50) topK = 50;
    }
  }

  /** 搜索响应。 */
  public record SearchResponse(String query, String docType, List<RagSearchHit> hits, int total) {}
}
