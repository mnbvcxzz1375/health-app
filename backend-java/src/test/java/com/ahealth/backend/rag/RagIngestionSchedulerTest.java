package com.ahealth.backend.rag;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RagIngestionScheduler 单元测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>Redis 不可用时跳过启动摄入</li>
 *   <li>Redis 文档数 >= 100 时跳过启动摄入</li>
 *   <li>Redis 文档数 < 100 时触发全量摄入</li>
 *   <li>定时摄入 consult_qa 调用 ingestByType</li>
 *   <li>异常情况不抛出（仅打 WARN 日志）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RagIngestionSchedulerTest {

  @Mock
  private RagIngestionService ragIngestionService;
  @Mock
  private RagRepository ragRepository;
  @Mock
  private JdbcTemplate jdbcTemplate;

  @InjectMocks
  private RagIngestionScheduler scheduler;

  @Test
  void ingestOnStartupSkipsWhenRedisUnavailable() {
    when(ragRepository.isAvailable()).thenReturn(false);

    scheduler.ingestOnStartup();

    // Redis 不可用时不应查询文档数，不应调用摄入
    verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Integer.class));
    verify(ragIngestionService, never()).ingestAll();
  }

  @Test
  void ingestOnStartupSkipsWhenDocsExceedThreshold() {
    when(ragRepository.isAvailable()).thenReturn(true);
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(150);

    scheduler.ingestOnStartup();

    // 文档数 >= 100 时不应调用摄入
    verify(ragIngestionService, never()).ingestAll();
  }

  @Test
  void ingestOnStartupTriggersWhenDocsBelowThreshold() throws InterruptedException {
    when(ragRepository.isAvailable()).thenReturn(true);
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(50);
    when(ragIngestionService.ingestAll()).thenReturn(List.of(
        new RagDtos.IngestResult("consult_qa", 10, 0, "OK"),
        new RagDtos.IngestResult("herb_guide", 5, 0, "OK")
    ));

    scheduler.ingestOnStartup();
    // 异步执行，等待线程完成
    Thread.sleep(500);

    verify(ragIngestionService, times(1)).ingestAll();
  }

  @Test
  void ingestOnStartupHandlesZeroDocs() throws InterruptedException {
    when(ragRepository.isAvailable()).thenReturn(true);
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
    when(ragIngestionService.ingestAll()).thenReturn(List.of());

    scheduler.ingestOnStartup();
    Thread.sleep(500);

    verify(ragIngestionService, times(1)).ingestAll();
  }

  @Test
  void ingestOnStartupHandlesDbQueryFailure() {
    when(ragRepository.isAvailable()).thenReturn(true);
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
        .thenThrow(new RuntimeException("DB 连接失败"));

    // 不应抛异常
    scheduler.ingestOnStartup();

    // DB 查询失败时不应调用摄入
    verify(ragIngestionService, never()).ingestAll();
  }

  @Test
  void ingestOnStartupHandlesIngestAllFailure() throws InterruptedException {
    when(ragRepository.isAvailable()).thenReturn(true);
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
    when(ragIngestionService.ingestAll()).thenThrow(new RuntimeException("Redis 写入失败"));

    // 不应抛异常
    scheduler.ingestOnStartup();
    Thread.sleep(500);

    // 异步线程内捕获异常，主线程不受影响
    verify(ragIngestionService, times(1)).ingestAll();
  }

  @Test
  void scheduledIngestConsultHistoryCallsIngestByType() {
    when(ragIngestionService.ingestByType("consult_qa"))
        .thenReturn(new RagDtos.IngestResult("consult_qa", 5, 0, "OK"));

    scheduler.scheduledIngestConsultHistory();

    verify(ragIngestionService, times(1)).ingestByType("consult_qa");
  }

  @Test
  void scheduledIngestConsultHistoryHandlesException() {
    when(ragIngestionService.ingestByType("consult_qa"))
        .thenThrow(new RuntimeException("Redis 不可用"));

    // 不应抛异常
    scheduler.scheduledIngestConsultHistory();

    verify(ragIngestionService, times(1)).ingestByType("consult_qa");
  }
}
