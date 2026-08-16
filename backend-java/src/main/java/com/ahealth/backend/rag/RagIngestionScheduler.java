package com.ahealth.backend.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * RAG 自动化摄入调度器：
 * <ol>
 *   <li>启动时（{@link ApplicationReadyEvent}）：仅在 Redis 中 rag_documents 行数 &lt; 100 时触发全量摄入，
 *       避免每次重启重复摄入。</li>
 *   <li>每天凌晨 3 点：增量摄入 {@code consult_qa}（每天可能有新增问诊记录）。</li>
 * </ol>
 *
 * <p>降级策略：所有调度任务 try-catch，失败仅 WARN 日志，不影响应用启动和主流程。
 */
@Service
public class RagIngestionScheduler {

  private static final Logger log = LoggerFactory.getLogger(RagIngestionScheduler.class);
  private static final int STARTUP_MIN_DOCS_THRESHOLD = 100;

  private final RagIngestionService ragIngestionService;
  private final RagRepository ragRepository;
  private final JdbcTemplate jdbcTemplate;

  public RagIngestionScheduler(
      RagIngestionService ragIngestionService,
      RagRepository ragRepository,
      JdbcTemplate jdbcTemplate
  ) {
    this.ragIngestionService = ragIngestionService;
    this.ragRepository = ragRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * 启动时首次摄入：仅在 Redis 为空（&lt; 100 条）时触发，避免重启重复摄入。
   * 异步执行，不阻塞应用启动。
   */
  @EventListener(ApplicationReadyEvent.class)
  public void ingestOnStartup() {
    try {
      if (!ragRepository.isAvailable()) {
        log.info("[RAG-Scheduler] Redis 不可用，跳过启动摄入");
        return;
      }
      int currentCount = countRagDocuments();
      if (currentCount >= STARTUP_MIN_DOCS_THRESHOLD) {
        log.info("[RAG-Scheduler] Redis 已有 {} 条文档，跳过启动摄入", currentCount);
        return;
      }
      log.info("[RAG-Scheduler] Redis 文档数 {} < {}，启动首次摄入...", currentCount, STARTUP_MIN_DOCS_THRESHOLD);
      // 异步执行避免阻塞启动
      new Thread(() -> {
        try {
          var results = ragIngestionService.ingestAll();
          int totalIngested = results.stream().mapToInt(RagDtos.IngestResult::ingested).sum();
          int totalFailed = results.stream().mapToInt(RagDtos.IngestResult::failed).sum();
          log.info("[RAG-Scheduler] 启动摄入完成: 成功={}, 失败={}", totalIngested, totalFailed);
        } catch (Exception e) {
          log.warn("[RAG-Scheduler] 启动摄入失败: {}", e.getMessage());
        }
      }, "rag-startup-ingest").start();
    } catch (Exception e) {
      log.warn("[RAG-Scheduler] 启动摄入初始化失败: {}", e.getMessage());
    }
  }

  /**
   * 每天凌晨 3 点增量摄入 consult_qa。
   * consult_history 表每天可能有新增问诊记录，需要定期摄入到 RAG。
   */
  @Scheduled(cron = "0 0 3 * * ?")
  public void scheduledIngestConsultHistory() {
    try {
      log.info("[RAG-Scheduler] 定时摄入 consult_qa 开始...");
      RagDtos.IngestResult result = ragIngestionService.ingestByType("consult_qa");
      log.info("[RAG-Scheduler] 定时摄入 consult_qa 完成: 成功={}, 失败={}",
          result.ingested(), result.failed());
    } catch (Exception e) {
      log.warn("[RAG-Scheduler] 定时摄入 consult_qa 失败: {}", e.getMessage());
    }
  }

  /** 统计 rag_documents 表总行数（fallback 到 0 表示可能表不存在或 Redis-only 模式）。 */
  private int countRagDocuments() {
    try {
      Integer count = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM rag_documents", Integer.class);
      return count == null ? 0 : count;
    } catch (Exception e) {
      // rag_documents 表可能不存在（Redis-only 模式），尝试用 Redis 索引信息
      log.debug("[RAG-Scheduler] 查询 rag_documents 失败，尝试 Redis 计数: {}", e.getMessage());
      return 0;
    }
  }
}
