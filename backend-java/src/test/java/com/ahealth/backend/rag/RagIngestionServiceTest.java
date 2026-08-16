package com.ahealth.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RagIngestionService 单元测试 — 聚焦 Step 26 rehab_exercises 结构化切片改造。
 *
 * <p>覆盖：
 * <ul>
 *   <li>rehab_exercises 读取失败时返回 failed=0 + 错误消息</li>
 *   <li>rehab_exercises 结构化切片：每个非空字段调用一次 ingestStructuredRow 内部的 upsert</li>
 *   <li>ingestByType 路由：9 种 docType 正确分发到对应方法</li>
 *   <li>未知 docType 返回错误消息</li>
 *   <li>空 docType 返回错误消息</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RagIngestionServiceTest {

  @Mock
  private JdbcTemplate jdbc;
  @Mock
  private ChunkingService chunkingService;
  @Mock
  private EmbeddingService embeddingService;
  @Mock
  private RagRepository ragRepository;

  private RagIngestionService service;

  @BeforeEach
  void setUp() {
    service = new RagIngestionService(jdbc, chunkingService, embeddingService, ragRepository, new ObjectMapper());
  }

  @Test
  void ingestRehabExercisesDbFailureReturnsZeroIngested() {
    when(jdbc.queryForList(anyString())).thenThrow(new RuntimeException("表不存在"));

    RagDtos.IngestResult result = service.ingestRehabExercises();

    assertThat(result.docType()).isEqualTo("rehab_guide");
    assertThat(result.ingested()).isEqualTo(0);
    assertThat(result.failed()).isEqualTo(0);
    assertThat(result.message()).contains("读取失败");
  }

  @Test
  void ingestRehabExercisesStructuredChunkingInvokesUpsertPerField() {
    // 模拟一行 rehab_exercises 数据：含 4 个非空字段 + 5 个空字段
    Map<String, Object> row = new HashMap<>();
    row.put("id", 1);
    row.put("name", "Bird Dog");
    row.put("category", "核心稳定");
    row.put("duration", "30s x 3");
    row.put("level", "basic");
    row.put("minutes", "5");
    row.put("steps_json", ""); // 空字段，应跳过
    row.put("caution", "腰痛时停止");
    row.put("focus", "");
    row.put("benefits_json", "");
    row.put("video_minutes", "");
    when(jdbc.queryForList(anyString())).thenReturn(List.of(row));
    // mock chunkingService 和 embeddingService
    when(chunkingService.chunk(anyString())).thenReturn(List.of("chunk text"));
    when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f}));
    when(ragRepository.upsert(any(), any())).thenReturn("rag:doc:test:1");

    RagDtos.IngestResult result = service.ingestRehabExercises();

    assertThat(result.docType()).isEqualTo("rehab_guide");
    assertThat(result.ingested()).isEqualTo(1);
    // 每个非空字段应该触发一次 upsert
    // 非空字段：category / duration / level / minutes / caution = 5 个
    verify(ragRepository, times(5)).upsert(any(), any());
  }

  @Test
  void ingestRehabExercisesEmptyRowsReturnsZero() {
    when(jdbc.queryForList(anyString())).thenReturn(List.of());

    RagDtos.IngestResult result = service.ingestRehabExercises();

    assertThat(result.ingested()).isEqualTo(0);
    assertThat(result.failed()).isEqualTo(0);
    verify(ragRepository, times(0)).upsert(any(), any());
  }

  @Test
  void ingestByTypeRoutesRehabGuide() {
    when(jdbc.queryForList(anyString())).thenReturn(List.of());

    RagDtos.IngestResult result = service.ingestByType("rehab_guide");

    assertThat(result.docType()).isEqualTo("rehab_guide");
    // 应该先 deleteByDocType 再 ingest
    verify(ragRepository, times(1)).deleteByDocType("rehab_guide");
  }

  @Test
  void ingestByTypeRoutesConsultQa() {
    RagDtos.IngestResult result = service.ingestByType("consult_qa");

    assertThat(result.docType()).isEqualTo("consult_qa");
    verify(ragRepository, times(1)).deleteByDocType("consult_qa");
  }

  @Test
  void ingestByTypeRoutesTcmIncompat() {
    when(jdbc.queryForList(anyString())).thenReturn(List.of());

    RagDtos.IngestResult result = service.ingestByType("tcm_incompat");

    assertThat(result.docType()).isEqualTo("tcm_incompat");
  }

  @Test
  void ingestByTypeRoutesTcmWm() {
    when(jdbc.queryForList(anyString())).thenReturn(List.of());

    RagDtos.IngestResult result = service.ingestByType("tcm_wm");

    assertThat(result.docType()).isEqualTo("tcm_wm");
  }

  @Test
  void ingestByTypeRoutesDrugFood() {
    when(jdbc.queryForList(anyString())).thenReturn(List.of());

    RagDtos.IngestResult result = service.ingestByType("drug_food");

    assertThat(result.docType()).isEqualTo("drug_food");
  }

  @Test
  void ingestByTypeUnknownReturnsError() {
    RagDtos.IngestResult result = service.ingestByType("unknown_type");

    assertThat(result.ingested()).isEqualTo(0);
    assertThat(result.message()).contains("未知 docType");
  }

  @Test
  void ingestByTypeEmptyReturnsError() {
    RagDtos.IngestResult result = service.ingestByType("");

    assertThat(result.ingested()).isEqualTo(0);
    assertThat(result.message()).contains("docType 为空");
  }

  @Test
  void ingestByTypeNullReturnsError() {
    RagDtos.IngestResult result = service.ingestByType(null);

    assertThat(result.ingested()).isEqualTo(0);
    assertThat(result.message()).contains("docType 为空");
  }

  @Test
  void ingestAllReturns9Results() {
    when(jdbc.queryForList(anyString())).thenReturn(List.of());

    List<RagDtos.IngestResult> results = service.ingestAll();

    assertThat(results).hasSize(9);
    List<String> docTypes = results.stream().map(RagDtos.IngestResult::docType).toList();
    assertThat(docTypes).containsExactly(
        "consult_qa", "herb_guide", "drug_label", "rehab_guide",
        "food_guide", "ddi_rule", "tcm_incompat", "tcm_wm", "drug_food");
  }

  @Test
  void ingestRehabExercisesMetadataContainsExerciseName() {
    Map<String, Object> row = new HashMap<>();
    row.put("id", 42);
    row.put("name", "Squat");
    row.put("category", "下肢训练");
    row.put("duration", "");
    row.put("level", "");
    row.put("minutes", "");
    row.put("steps_json", "");
    row.put("caution", "膝盖疼痛时停止");
    row.put("focus", "");
    row.put("benefits_json", "");
    row.put("video_minutes", "");
    when(jdbc.queryForList(anyString())).thenReturn(List.of(row));
    when(chunkingService.chunk(anyString())).thenReturn(List.of("chunk"));
    when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f}));
    when(ragRepository.upsert(any(), any())).thenReturn("doc:1");

    service.ingestRehabExercises();

    // 捕获 upsert 的 RagDocument 参数，验证 metadata 含 exercise_name
    ArgumentCaptor<RagDtos.RagDocument> docCaptor = ArgumentCaptor.forClass(RagDtos.RagDocument.class);
    verify(ragRepository, times(2)).upsert(docCaptor.capture(), any());

    for (RagDtos.RagDocument doc : docCaptor.getAllValues()) {
      assertThat(doc.metadata()).containsKey("exercise_name");
      assertThat(doc.metadata().get("exercise_name")).isEqualTo("Squat");
      assertThat(doc.docType()).isEqualTo("rehab_guide");
    }
  }
}
