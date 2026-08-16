package com.ahealth.backend.knowledge;

import com.ahealth.backend.ai.DashScopeService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 多药材同图识别 + 去重。
 * 路径：local_medication_api YOLO → LLM 多模态 fallback → 查 tcm_herbs 补全信息。
 */
@Service
public class MultiHerbRecognitionService {
  private static final Logger log = LoggerFactory.getLogger(MultiHerbRecognitionService.class);

  private final JdbcTemplate jdbcTemplate;
  private final DashScopeService dashScopeService;
  private final RestTemplate restTemplate;
  private final com.ahealth.backend.ai.PromptTemplateService promptTemplateService;

  public MultiHerbRecognitionService(JdbcTemplate jdbcTemplate, DashScopeService dashScopeService,
      com.ahealth.backend.ai.PromptTemplateService promptTemplateService) {
    this.jdbcTemplate = jdbcTemplate;
    this.dashScopeService = dashScopeService;
    this.promptTemplateService = promptTemplateService;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5_000);
    factory.setReadTimeout(8_000);
    this.restTemplate = new RestTemplate(factory);
  }

  public KnowledgeDtos.HerbRecognitionResult recognize(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return new KnowledgeDtos.HerbRecognitionResult(List.of(), List.of(), 0.0);
    }

    List<RawHerb> rawHerbs = new ArrayList<>();

    // Step 1: 尝试本地 YOLO 端点（如可用）
    try {
      List<RawHerb> yoloResult = callLocalYolo(file);
      if (!yoloResult.isEmpty()) {
        rawHerbs = yoloResult;
      }
    } catch (Exception e) {
      log.warn("[MultiHerbRecognition] YOLO 端点调用失败，回退到 LLM: {}", e.getMessage());
    }

    // Step 2: YOLO 无结果 → 调 LLM 多模态
    if (rawHerbs.isEmpty()) {
      try {
        rawHerbs = callLlmVision(file);
      } catch (Exception e) {
        log.warn("[MultiHerbRecognition] LLM 多模态识别失败: {}", e.getMessage());
        return new KnowledgeDtos.HerbRecognitionResult(List.of(), List.of(), 0.0);
      }
    }

    // Step 3: 按名去重
    LinkedHashMap<String, RawHerb> deduped = new LinkedHashMap<>();
    List<String> duplicates = new ArrayList<>();
    for (RawHerb herb : rawHerbs) {
      String name = herb.name().trim();
      if (name.isBlank()) continue;
      if (deduped.containsKey(name)) {
        duplicates.add(name);
      } else {
        deduped.put(name, herb);
      }
    }

    // Step 4: 批量查 tcm_herbs 补全信息
    List<KnowledgeDtos.HerbRecognitionItem> items = new ArrayList<>();
    double confidenceSum = 0;
    int confidenceCount = 0;
    for (RawHerb herb : deduped.values()) {
      var tcmOpt = lookupHerb(herb.name());
      KnowledgeDtos.HerbRecognitionItem item = new KnowledgeDtos.HerbRecognitionItem(
          herb.name(),
          tcmOpt.map(HerbInfo::pinyin).orElse(""),
          tcmOpt.map(HerbInfo::nature).orElse(""),
          tcmOpt.map(HerbInfo::flavor).orElse(""),
          tcmOpt.map(HerbInfo::meridian).orElse(""),
          tcmOpt.map(HerbInfo::efficacy).orElse(""),
          herb.confidence(),
          tcmOpt.map(info -> "知识库").orElse("LLM 识别")
      );
      items.add(item);
      if (herb.confidence() > 0) {
        confidenceSum += herb.confidence();
        confidenceCount++;
      }
    }

    double avgConfidence = confidenceCount == 0 ? 0.0 : Math.round((confidenceSum / confidenceCount) * 100.0) / 100.0;
    return new KnowledgeDtos.HerbRecognitionResult(items, duplicates, avgConfidence);
  }

  private List<RawHerb> callLocalYolo(MultipartFile file) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.MULTIPART_FORM_DATA);
      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      var resource = new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
        @Override
        public String getFilename() {
          return file.getOriginalFilename() == null ? "herb.jpg" : file.getOriginalFilename();
        }
      };
      body.add("file", resource);

      HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
      String response = restTemplate.postForObject(
          "http://127.0.0.1:8011/recognize-herbs",
          entity,
          String.class
      );
      if (response == null || response.isBlank()) {
        return List.of();
      }
      return parseRawList(dashScopeService.extractJsonObject(response));
    } catch (Exception e) {
      log.debug("[MultiHerbRecognition] YOLO 端点不可用: {}", e.getMessage());
      return List.of();
    }
  }

  private List<RawHerb> callLlmVision(MultipartFile file) throws Exception {
    List<Map<String, Object>> content = new ArrayList<>();
    content.add(Map.of("type", "text", "text", "请识别图片中所有可见的中药材。"));
    content.addAll(dashScopeService.toImageBlocks(new MultipartFile[]{file}));

    JsonNode payload = dashScopeService.requestJson(
        promptTemplateService.render("herb_recognition.system", Map.of()),
        content,
        dashScopeService.visionModel(),
        0.1,
        "多药材识别"
    );
    return parseRawList(payload);
  }

  private List<RawHerb> parseRawList(JsonNode node) {
    List<RawHerb> result = new ArrayList<>();
    if (node == null) return result;
    JsonNode array = node.isArray() ? node : node.path("items");
    if (!array.isArray()) return result;
    for (JsonNode item : array) {
      String name = item.path("name").asText("").trim();
      if (name.isBlank()) continue;
      double conf = item.path("confidence").isNumber() ? item.path("confidence").doubleValue() : 0.8;
      result.add(new RawHerb(name, conf));
    }
    return result;
  }

  private java.util.Optional<HerbInfo> lookupHerb(String name) {
    try {
      var row = jdbcTemplate.queryForMap(
          "SELECT name, pinyin, nature, flavor, meridian, efficacy FROM tcm_herbs WHERE name = ? LIMIT 1",
          name
      );
      return java.util.Optional.of(new HerbInfo(
          String.valueOf(row.get("pinyin")),
          String.valueOf(row.get("nature")),
          String.valueOf(row.get("flavor")),
          String.valueOf(row.get("meridian")),
          String.valueOf(row.get("efficacy"))
      ));
    } catch (EmptyResultDataAccessException e) {
      return java.util.Optional.empty();
    }
  }

  private record RawHerb(String name, double confidence) {}

  private record HerbInfo(String pinyin, String nature, String flavor, String meridian, String efficacy) {}
}
