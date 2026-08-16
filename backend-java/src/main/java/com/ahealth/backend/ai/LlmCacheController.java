package com.ahealth.backend.ai;

import com.ahealth.backend.security.AdminAccessService;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM 缓存管理端点：统计、清理、全量清空。
 *
 * <p>路径前缀 /api/admin/cache，所有端点都需要管理员认证。
 */
@RestController
@RequestMapping("/api/admin/cache")
public class LlmCacheController {

  private final LlmCacheService llmCacheService;
  private final AdminAccessService adminAccessService;

  public LlmCacheController(LlmCacheService llmCacheService, AdminAccessService adminAccessService) {
    this.llmCacheService = llmCacheService;
    this.adminAccessService = adminAccessService;
  }

  /** 查询缓存统计：各 scene 条目数、hit_count 总和、本地缓存大小、语义阈值。 */
  @GetMapping("/stats")
  public Map<String, Object> stats() {
    adminAccessService.requireAdmin();
    return llmCacheService.stats();
  }

  /** 按场景清理缓存（MySQL + 向量库 + 本地）。 */
  @PostMapping("/evict")
  public Map<String, Object> evict(@RequestParam String scene) {
    adminAccessService.requireAdmin();
    llmCacheService.evict(scene);
    return Map.of("success", true, "evictedScene", scene);
  }

  /** 全量清空所有场景缓存。 */
  @DeleteMapping("/all")
  public Map<String, Object> evictAll() {
    adminAccessService.requireAdmin();
    llmCacheService.evictAll();
    return Map.of("success", true);
  }
}
