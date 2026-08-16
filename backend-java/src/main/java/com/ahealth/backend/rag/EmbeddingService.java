package com.ahealth.backend.rag;

import com.ahealth.backend.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Embedding 服务：调用 DashScope text-embedding-v3，输出 1024 维向量。
 *
 * <p>与 DashScopeService 分离，因为 embedding 端点是 /embeddings（非 /chat/completions）。
 * 复用相同的 apiKey / baseUrl 配置。
 *
 * <p>批量上限：DashScope text-embedding-v3 单次最多 25 条文本，超过自动分批。
 */
@Service
public class EmbeddingService {

  private static final int BATCH_LIMIT = 25;

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final String apiKey;
  private final String baseUrl;
  private final String embeddingModel;

  // Query embedding 缓存：仅 embed(String) 单条查询时缓存；embedBatch() 不缓存（用于摄入，量大且不复用）
  // TTL 30 分钟，最多 500 条，避免热门 query 重复调用 DashScope API
  private final Cache<String, float[]> queryCache = Caffeine.newBuilder()
      .expireAfterWrite(30, TimeUnit.MINUTES)
      .maximumSize(500)
      .build();
  private final AtomicLong queryCacheHits = new AtomicLong();
  private final AtomicLong queryCacheMisses = new AtomicLong();

  public EmbeddingService(
      ObjectMapper objectMapper,
      @Value("${DASHSCOPE_API_KEY:${QWEN_API_KEY:}}") String apiKey,
      @Value("${DASHSCOPE_BASE_URL:https://coding.dashscope.aliyuncs.com/v1}") String baseUrl,
      @Value("${DASHSCOPE_EMBEDDING_MODEL:text-embedding-v3}") String embeddingModel
  ) {
    this.objectMapper = objectMapper;
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.baseUrl = (baseUrl == null || baseUrl.isBlank())
        ? "https://coding.dashscope.aliyuncs.com/v1" : baseUrl.trim();
    this.embeddingModel = (embeddingModel == null || embeddingModel.isBlank())
        ? "text-embedding-v3" : embeddingModel.trim();
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }

  public boolean isConfigured() {
    return !apiKey.isBlank();
  }

  public String embeddingModel() {
    return embeddingModel;
  }

  /**
   * 单条文本 embedding。命中 query 缓存直接返回，未命中调 batch API 后回填。
   * 失败抛 ApiException。仅此方法走缓存，{@link #embedBatch} 不缓存。
   */
  public float[] embed(String text) {
    if (!isConfigured()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Embedding 未配置 DASHSCOPE_API_KEY");
    }
    if (text == null || text.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Embedding 输入文本为空");
    }
    float[] cached = queryCache.getIfPresent(text);
    if (cached != null) {
      queryCacheHits.incrementAndGet();
      return cached;
    }
    queryCacheMisses.incrementAndGet();
    List<float[]> batch = embedBatch(List.of(text));
    float[] result = batch.get(0);
    queryCache.put(text, result);
    return result;
  }

  /** Query embedding 缓存统计（供 admin 端点观测命中率）。 */
  public Map<String, Object> queryCacheStats() {
    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("hits", queryCacheHits.get());
    stats.put("misses", queryCacheMisses.get());
    stats.put("size", queryCache.estimatedSize());
    long total = queryCacheHits.get() + queryCacheMisses.get();
    stats.put("hitRate", total == 0 ? 0.0 : (double) queryCacheHits.get() / total);
    return stats;
  }

  /**
   * 批量 embedding：单次最多 25 条，超过自动分批。
   * 返回顺序与输入一致。
   */
  public List<float[]> embedBatch(List<String> texts) {
    if (texts == null || texts.isEmpty()) {
      return List.of();
    }
    if (!isConfigured()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Embedding 未配置 DASHSCOPE_API_KEY");
    }
    List<float[]> result = new ArrayList<>(texts.size());
    for (int i = 0; i < texts.size(); i += BATCH_LIMIT) {
      int end = Math.min(i + BATCH_LIMIT, texts.size());
      List<String> batch = texts.subList(i, end);
      result.addAll(callEmbeddingApi(batch));
    }
    return result;
  }

  private List<float[]> callEmbeddingApi(List<String> texts) {
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", embeddingModel);
      body.put("input", texts);
      body.put("encoding_format", "float");

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/embeddings"))
          .timeout(Duration.ofSeconds(60))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + apiKey)
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "Embedding 调用失败: " + response.body());
      }

      JsonNode root = objectMapper.readTree(response.body());
      JsonNode data = root.path("data");
      if (!data.isArray()) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "Embedding 返回格式无效");
      }
      List<float[]> result = new ArrayList<>();
      for (JsonNode item : data) {
        JsonNode embNode = item.path("embedding");
        if (!embNode.isArray()) {
          throw new ApiException(HttpStatus.BAD_GATEWAY, "Embedding 返回向量格式无效");
        }
        float[] vec = new float[embNode.size()];
        for (int i = 0; i < embNode.size(); i++) {
          vec[i] = (float) embNode.get(i).asDouble();
        }
        result.add(vec);
      }
      return result;
    } catch (ApiException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.BAD_GATEWAY, "Embedding 调用被中断");
    } catch (IOException e) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "Embedding 调用失败: " + e.getMessage());
    }
  }
}
