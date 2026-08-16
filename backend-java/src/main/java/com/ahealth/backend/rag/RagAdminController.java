package com.ahealth.backend.rag;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 管理端点：摄入、检索、统计、删除。
 *
 * <p>路径前缀 /api/admin/rag，所有端点都需要登录认证，且当前用户必须在 admin 白名单内
 * （通过 {@code custom.admin.user-ids} 配置，默认用户 ID=1）。
 */
@RestController
@RequestMapping("/api/admin/rag")
public class RagAdminController {

  private final RagIngestionService ragIngestionService;
  private final RagSearchService ragSearchService;
  private final RagRepository ragRepository;
  private final JdbcTemplate jdbcTemplate;
  private final Set<Long> adminUserIds;

  public RagAdminController(
      RagIngestionService ragIngestionService,
      RagSearchService ragSearchService,
      RagRepository ragRepository,
      JdbcTemplate jdbcTemplate,
      @Value("${custom.admin.user-ids:1}") String adminUserIds
  ) {
    this.ragIngestionService = ragIngestionService;
    this.ragSearchService = ragSearchService;
    this.ragRepository = ragRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.adminUserIds = parseAdminUserIds(adminUserIds);
  }

  /** 解析 admin user-ids 配置（逗号分隔字符串 → Set<Long>）。 */
  private static Set<Long> parseAdminUserIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return Set.of();
    }
    return java.util.Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> {
          try {
            return Long.parseLong(s);
          } catch (NumberFormatException e) {
            return null;
          }
        })
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
  }

  /** 校验当前登录用户是否为 admin，否则抛 403。 */
  private void requireAdmin() {
    long uid = CurrentUser.requireUserId();
    if (!adminUserIds.contains(uid)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "仅管理员可执行此操作");
    }
  }

  /**
   * 触发摄入：type 为空调 ingestAll()，否则调 ingestByType(type)。
   * 支持的 type：consult_qa / herb_guide / drug_label / rehab_guide / food_guide / ddi_rule /
   * tcm_incompat / tcm_wm / drug_food
   */
  @PostMapping("/ingest")
  public Object ingest(@RequestParam(required = false) String type) {
    requireAdmin();
    if (type == null || type.isBlank()) {
      List<RagDtos.IngestResult> results = ragIngestionService.ingestAll();
      int totalIngested = results.stream().mapToInt(RagDtos.IngestResult::ingested).sum();
      int totalFailed = results.stream().mapToInt(RagDtos.IngestResult::failed).sum();
      return Map.of(
          "success", true,
          "results", results,
          "totalIngested", totalIngested,
          "totalFailed", totalFailed
      );
    }
    RagDtos.IngestResult result = ragIngestionService.ingestByType(type.trim());
    return Map.of("success", true, "results", List.of(result));
  }

  /** 检索：调 RagSearchService.search。仅 admin 可访问（避免普通用户绕过业务层直查 RAG）。 */
  @GetMapping("/search")
  public RagDtos.SearchResponse search(
      @RequestParam("q") String q,
      @RequestParam(required = false) String docType,
      @RequestParam(required = false, defaultValue = "5") int topK
  ) {
    requireAdmin();
    List<RagDtos.RagSearchHit> hits = ragSearchService.search(q, docType, topK);
    return new RagDtos.SearchResponse(q, docType, hits, hits.size());
  }

  /** 统计：rag_documents 表按 doc_type 分组 count + 当前 repositoryType。 */
  @GetMapping("/stats")
  public Object stats() {
    requireAdmin();
    List<Map<String, Object>> rows;
    try {
      rows = jdbcTemplate.queryForList(
          "SELECT doc_type, COUNT(*) AS cnt FROM rag_documents GROUP BY doc_type ORDER BY doc_type");
    } catch (Exception e) {
      // 表不存在或 Redis-only 模式（无 MySQL 镜像）
      rows = List.of();
    }
    Map<String, Object> stats = new HashMap<>();
    stats.put("repositoryType", ragRepository.repositoryType());
    stats.put("available", ragRepository.isAvailable());
    stats.put("byDocType", rows);
    int total = rows.stream()
        .mapToInt(r -> ((Number) r.getOrDefault("cnt", 0)).intValue())
        .sum();
    stats.put("total", total);
    return stats;
  }

  /** 删除指定文档。 */
  @DeleteMapping("/doc/{redisDocId}")
  public Object delete(@PathVariable String redisDocId) {
    requireAdmin();
    ragRepository.delete(redisDocId);
    return Map.of("success", true, "deleted", redisDocId);
  }
}
