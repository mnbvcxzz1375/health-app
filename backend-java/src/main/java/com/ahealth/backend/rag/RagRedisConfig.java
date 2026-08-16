package com.ahealth.backend.rag;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

/**
 * Redis Stack 配置：启动时探测 Redis 并创建 RediSearch 向量索引。
 *
 * <p>索引结构（HNSW + COSINE，1024 维 DashScope text-embedding-v3）：
 * <ul>
 *   <li>doc_type TAG — 文档类型过滤</li>
 *   <li>source_table TAG / source_id NUMERIC — 来源元信息</li>
 *   <li>title TEXT / chunk_text TEXT — 全文检索字段</li>
 *   <li>chunk_index NUMERIC / token_count NUMERIC — 切片元信息</li>
 *   <li>embedding VECTOR HNSW 12 TYPE FLOAT32 DIM 1024 DISTANCE_METRIC COSINE</li>
 * </ul>
 *
 * <p>Redis Stack 不可用时仅打 WARN 日志，不阻止启动（fallback 到 RagInMemoryRepository）。
 */
@Configuration
@ConditionalOnProperty(prefix = "rag.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagRedisConfig {

  private static final Logger log = LoggerFactory.getLogger(RagRedisConfig.class);

  @Value("${spring.data.redis.host:127.0.0.1}")
  private String redisHost;

  @Value("${spring.data.redis.port:6379}")
  private int redisPort;

  @Value("${spring.data.redis.password:}")
  private String redisPassword;

  @Value("${rag.redis.index-name:ahealth_rag}")
  private String indexName;

  @Value("${rag.redis.prefix:rag:doc:}")
  private String keyPrefix;

  @Value("${rag.redis.vector-dimension:1024}")
  private int vectorDimension;

  @Value("${rag.redis.distance-metric:COSINE}")
  private String distanceMetric;

  /** HNSW M 参数：每个节点的最大邻居数，越大精度越高。默认 32（RediSearch 默认 16）。 */
  @Value("${rag.redis.hnsw-m:32}")
  private int hnswM;

  /** HNSW EF_CONSTRUCTION：建索引时候选邻居池大小，越大索引质量越好。默认 400（RediSearch 默认 200）。 */
  @Value("${rag.redis.hnsw-ef-construction:400}")
  private int hnswEfConstruction;

  /** HNSW EF_RUNTIME：查询时候选邻居池大小，越大召回越准。默认 50（RediSearch 默认 10）。 */
  @Value("${rag.redis.hnsw-ef-runtime:50}")
  private int hnswEfRuntime;

  private JedisPool jedisPool;
  private JedisPooled jedisPooled;

  @Bean
  public JedisPool ragJedisPool() {
    if (jedisPool != null) {
      return jedisPool;
    }
    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(16);
    poolConfig.setMaxIdle(8);
    poolConfig.setMinIdle(2);
    poolConfig.setTestOnBorrow(false);
    poolConfig.setTestWhileIdle(true);

    if (redisPassword == null || redisPassword.isBlank()) {
      jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 5000);
    } else {
      jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 5000, redisPassword);
    }
    return jedisPool;
  }

  /**
   * Jedis 5.x 推荐的线程安全客户端，直接调用 ftSearch / ftCreate / ftInfo 等
   * RediSearch 命令（{@link redis.clients.jedis.Jedis} 类在 5.x 中已移除这些方法）。
   */
  @Bean
  @Primary
  public JedisPooled ragJedisPooled() {
    if (jedisPooled != null) {
      return jedisPooled;
    }
    if (redisPassword == null || redisPassword.isBlank()) {
      jedisPooled = new JedisPooled(redisHost, redisPort);
    } else {
      jedisPooled = new JedisPooled(redisHost, redisPort, null, redisPassword);
    }
    return jedisPooled;
  }

  public String indexName() {
    return indexName;
  }

  public String keyPrefix() {
    return keyPrefix;
  }

  public int vectorDimension() {
    return vectorDimension;
  }

  @PostConstruct
  public void ensureIndex() {
    try {
      JedisPooled jedis = ragJedisPooled();
      String pong = jedis.ping();
      if (!"PONG".equalsIgnoreCase(pong)) {
        log.warn("[RagRedis] Redis PING 返回异常: {}，跳过索引创建", pong);
        return;
      }
      createIndexIfNotExists(jedis);
      log.info("[RagRedis] 索引 {} 已就绪（prefix={} dim={} metric={} M={} EF_C={} EF_R={}）",
          indexName, keyPrefix, vectorDimension, distanceMetric, hnswM, hnswEfConstruction, hnswEfRuntime);
    } catch (JedisConnectionException e) {
      log.warn("[RagRedis] Redis Stack 不可用，将 fallback 到 InMemoryRepository: {}", e.getMessage());
    } catch (Exception e) {
      log.warn("[RagRedis] 索引初始化失败（不影响启动）: {}", e.getMessage());
    }
  }

  /** 探测 Redis 是否可用（供 RagRedisRepository.isAvailable() 使用）。 */
  public boolean isRedisAvailable() {
    try {
      ragJedisPooled().ping();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private void createIndexIfNotExists(JedisPooled jedis) {
    // 先尝试 FT.INFO 探测索引是否已存在
    try {
      jedis.ftInfo(indexName);
      return; // 不抛异常即表示索引存在
    } catch (Exception ignored) {
      // 索引不存在，继续创建
    }

    // 构造 schema fields（Jedis 5.x 新签名：Iterable<SchemaField>）
    List<SchemaField> fields = List.of(
        TagField.of("doc_type"),
        TagField.of("source_table"),
        NumericField.of("source_id"),
        TextField.of("title"),
        TextField.of("chunk_text"),
        NumericField.of("chunk_index"),
        NumericField.of("token_count"),
        VectorField.builder()
            .fieldName("embedding")
            .algorithm(VectorField.VectorAlgorithm.HNSW)
            .addAttribute("TYPE", "FLOAT32")
            .addAttribute("DIM", String.valueOf(vectorDimension))
            .addAttribute("DISTANCE_METRIC", distanceMetric)
            .addAttribute("M", String.valueOf(hnswM))
            .addAttribute("EF_CONSTRUCTION", String.valueOf(hnswEfConstruction))
            .addAttribute("EF_RUNTIME", String.valueOf(hnswEfRuntime))
            .build()
    );

    FTCreateParams params = FTCreateParams.createParams()
        .on(IndexDataType.HASH)
        .prefix(keyPrefix);

    try {
      String result = jedis.ftCreate(indexName, params, fields);
      log.info("[RagRedis] 索引 {} 创建结果: {}", indexName, result);
    } catch (Exception e) {
      log.warn("[RagRedis] 索引创建命令异常（可能已存在）: {}", e.getMessage());
    }
  }
}
