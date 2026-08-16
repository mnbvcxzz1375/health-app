package com.ahealth.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ahealth.backend.ai.DashScopeService;
import com.ahealth.backend.ai.LlmCacheService;
import com.ahealth.backend.ai.PromptTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Reranker 单元测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>缓存命中：直接返回缓存评分，不调 LLM</li>
 *   <li>缓存未命中：调 LLM 评分并写缓存</li>
 *   <li>LLM 返回非数组：fallback 到原顺序</li>
 *   <li>空 / null 候选列表边界情况</li>
 *   <li>topK >= candidates.size() 时直接返回</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RerankerTest {

  @Mock
  private DashScopeService dashScopeService;
  @Mock
  private PromptTemplateService promptTemplateService;
  @Mock
  private LlmCacheService llmCacheService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private Reranker reranker;

  @BeforeEach
  void setUp() {
    reranker = new Reranker(dashScopeService, objectMapper, promptTemplateService, llmCacheService);
  }

  private List<RagDtos.RagSearchHit> buildCandidates(int n) {
    List<RagDtos.RagSearchHit> list = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      list.add(new RagDtos.RagSearchHit(
          "doc:" + i, "drug_label", "药品" + i,
          "这是药品" + i + "的说明文本", 0.5 + i * 0.01, Map.of()));
    }
    return list;
  }

  private JsonNode buildLlmResponse(int[] scores) {
    ArrayNode array = objectMapper.createArrayNode();
    for (int i = 0; i < scores.length; i++) {
      var obj = objectMapper.createObjectNode();
      obj.put("id", i + 1);
      obj.put("score", scores[i]);
      obj.put("reason", "测试理由");
      array.add(obj);
    }
    return array;
  }

  @Test
  void rerankEmptyCandidatesReturnsEmpty() {
    assertThat(reranker.rerank("query", List.of(), 5)).isEmpty();
  }

  @Test
  void rerankNullCandidatesReturnsEmpty() {
    assertThat(reranker.rerank("query", null, 5)).isEmpty();
  }

  @Test
  void rerankTopKZeroReturnsEmpty() {
    assertThat(reranker.rerank("query", buildCandidates(10), 0)).isEmpty();
  }

  @Test
  void rerankCandidatesSizeLessOrEqualTopKReturnsOriginal() {
    List<RagDtos.RagSearchHit> candidates = buildCandidates(3);
    List<RagDtos.RagSearchHit> result = reranker.rerank("query", candidates, 5);
    assertThat(result).hasSize(3);
    // 不调 LLM
    verify(dashScopeService, never()).requestJson(anyString(), any(), anyString(), anyDouble(), anyString());
  }

  @Test
  void rerankCacheHitReturnsCachedScoresWithoutCallingLlm() {
    List<RagDtos.RagSearchHit> candidates = buildCandidates(10);
    // 缓存返回反转的分数（id=1 → score=1, id=2 → score=2, ...）
    String cachedJson = buildLlmResponse(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}).toString();
    when(llmCacheService.getExact(eq("rag_rerank"), anyString(), anyString()))
        .thenReturn(Optional.of(cachedJson));

    List<RagDtos.RagSearchHit> result = reranker.rerank("query", candidates, 5);

    assertThat(result).hasSize(5);
    // 缓存命中后不应调 LLM
    verify(dashScopeService, never()).requestJson(anyString(), any(), anyString(), anyDouble(), anyString());
    // 缓存命中后不应再写缓存
    verify(llmCacheService, never()).put(anyString(), anyString(), anyString(), anyString(), any(), eq(false));
    // 验证排序：分数高的在前（id=10 > id=9 > ... > id=6）
    assertThat(result.get(0).title()).isEqualTo("药品9"); // id=10, index=9
    assertThat(result.get(4).title()).isEqualTo("药品5"); // id=6, index=5
  }

  @Test
  void rerankCacheMissCallsLlmAndWritesCache() {
    List<RagDtos.RagSearchHit> candidates = buildCandidates(10);
    when(llmCacheService.getExact(anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(promptTemplateService.render(eq("rag.rerank_system"), any())).thenReturn("system prompt");
    when(dashScopeService.chatModel()).thenReturn("kimi-k2.5");
    when(dashScopeService.requestJson(anyString(), any(), eq("kimi-k2.5"), eq(0.0), eq("Rerank")))
        .thenReturn(buildLlmResponse(new int[]{9, 8, 7, 6, 5, 4, 3, 2, 1, 0}));

    List<RagDtos.RagSearchHit> result = reranker.rerank("query", candidates, 3);

    assertThat(result).hasSize(3);
    // 第一个应该是 id=1（score=9，最高）
    assertThat(result.get(0).title()).isEqualTo("药品0");
    // 验证写缓存
    verify(llmCacheService, times(1)).put(eq("rag_rerank"), anyString(), anyString(), anyString(), any(), eq(false));
  }

  @Test
  void rerankLlmReturnsNonArrayFallsBackToOriginalOrder() {
    List<RagDtos.RagSearchHit> candidates = buildCandidates(10);
    when(llmCacheService.getExact(anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(promptTemplateService.render(anyString(), any())).thenReturn("sys");
    when(dashScopeService.chatModel()).thenReturn("model");
    when(dashScopeService.requestJson(anyString(), any(), anyString(), anyDouble(), anyString()))
        .thenReturn(objectMapper.createObjectNode()); // 非数组

    List<RagDtos.RagSearchHit> result = reranker.rerank("query", candidates, 3);

    assertThat(result).hasSize(3);
    // fallback 到原顺序前 3 个
    assertThat(result.get(0).title()).isEqualTo("药品0");
    assertThat(result.get(1).title()).isEqualTo("药品1");
    assertThat(result.get(2).title()).isEqualTo("药品2");
  }

  @Test
  void rerankLlmThrowsFallsBackToOriginalOrder() {
    List<RagDtos.RagSearchHit> candidates = buildCandidates(10);
    when(llmCacheService.getExact(anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(promptTemplateService.render(anyString(), any())).thenReturn("sys");
    when(dashScopeService.chatModel()).thenReturn("model");
    when(dashScopeService.requestJson(anyString(), any(), anyString(), anyDouble(), anyString()))
        .thenThrow(new RuntimeException("LLM 不可用"));

    List<RagDtos.RagSearchHit> result = reranker.rerank("query", candidates, 3);

    assertThat(result).hasSize(3);
    assertThat(result.get(0).title()).isEqualTo("药品0");
  }

  @Test
  void rerankMoreThanMaxChunksPerCallTruncatedTo20() {
    // 25 个候选，应该被截断到 20 个
    List<RagDtos.RagSearchHit> candidates = buildCandidates(25);
    when(llmCacheService.getExact(anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(promptTemplateService.render(anyString(), any())).thenReturn("sys");
    when(dashScopeService.chatModel()).thenReturn("model");
    // LLM 返回 20 个评分（id 1-20）
    when(dashScopeService.requestJson(anyString(), any(), anyString(), anyDouble(), anyString()))
        .thenReturn(buildLlmResponse(new int[]{20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1}));

    List<RagDtos.RagSearchHit> result = reranker.rerank("query", candidates, 5);

    assertThat(result).hasSize(5);
    // 第一个应该是 id=1（score=20，最高），对应 index=0 的"药品0"
    assertThat(result.get(0).title()).isEqualTo("药品0");
  }

  @Test
  void rerankCacheHitButParseFailsFallsBackToLlm() {
    List<RagDtos.RagSearchHit> candidates = buildCandidates(10);
    // 缓存返回的 JSON 格式错误
    when(llmCacheService.getExact(anyString(), anyString(), anyString()))
        .thenReturn(Optional.of("not a valid json array"));
    when(promptTemplateService.render(anyString(), any())).thenReturn("sys");
    when(dashScopeService.chatModel()).thenReturn("model");
    when(dashScopeService.requestJson(anyString(), any(), anyString(), anyDouble(), anyString()))
        .thenReturn(buildLlmResponse(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}));

    List<RagDtos.RagSearchHit> result = reranker.rerank("query", candidates, 3);

    assertThat(result).hasSize(3);
    // 缓存解析失败后应该 fallback 到 LLM
    verify(dashScopeService, times(1)).requestJson(anyString(), any(), anyString(), anyDouble(), anyString());
  }
}
