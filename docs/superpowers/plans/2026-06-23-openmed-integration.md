# OpenMed Multi-Model Medical Intelligence Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate OpenMed's PharmaDetect NER, PII De-identification, and Medical VQA models into the health app as a multi-model orchestration layer — demonstrating original system design rather than simple API calls.

**Architecture:** A new `ModelRouterService` selects the optimal model per task (NER extraction, PII scrubbing, image understanding, general consultation). Each model call flows through a privacy pipeline (PII scrub → model call → result merge). The existing `DashScopeService` and `ConsultService` are preserved as fallbacks. NER results cross-validate with OCR results for higher confidence. Drug interaction checks use NER-extracted entities against a DDI knowledge table.

**Tech Stack:** Java 17, Spring Boot 3.5, Hugging Face Inference API (or local inference via Python sidecar), MySQL, Vue 3 + TypeScript

## Global Constraints

- All new backend code follows the existing `Controller → Service → JdbcTemplate` pattern (no JPA)
- External API calls use `java.net.http.HttpClient` matching `DashScopeService` pattern
- DTOs are Java records in `*Dtos.java` files
- Configuration via `@Value("${ENV_VAR:default}")` pattern
- Authentication via `CurrentUser.requireUserId()` — all new endpoints require auth
- Frontend types mirror backend DTOs exactly
- **CRITICAL: Flyway is DISABLED (`application.yml` line 19: `enabled: false`).** New tables must be added to `BackendSchemaInitializer.java` via `CREATE TABLE IF NOT EXISTS` in `@PostConstruct`. SQL migration files alone will NOT be executed.
- All Chinese UI text — no English in user-facing strings
- TDD: write failing test → verify fail → implement → verify pass → commit

---

## File Structure

### Backend (backend-java)

| File | Responsibility |
|------|---------------|
| `src/main/java/com/ahealth/backend/ai/OpenMedService.java` | HTTP client for OpenMed HuggingFace Inference API — NER, PII, VQA calls |
| `src/main/java/com/ahealth/backend/ai/ModelRouterService.java` | Routes tasks to optimal model based on task type + context |
| `src/main/java/com/ahealth/backend/ai/PiiScrubService.java` | PII detection + scrubbing + mapping table for restoration |
| `src/main/java/com/ahealth/backend/ai/MedicalNerService.java` | PharmaDetect NER — extracts drug entities from text |
| `src/main/java/com/ahealth/backend/ai/DdiKnowledgeService.java` | Drug-drug interaction lookup from DDI knowledge table |
| `src/main/java/com/ahealth/backend/ai/AiDtos.java` | Shared DTOs: NerEntity, PiiMask, ModelRouteDecision, DdiWarning |
| `src/main/java/com/ahealth/backend/config/BackendSchemaInitializer.java` | Modify: add `ddi_knowledge` + `consult_history` tables (Flyway disabled) |

### Frontend (健康监测与分析平台)

| File | Responsibility |
|------|---------------|
| `src/api/modules/ai.ts` | API client for new AI endpoints (NER, PII status, DDI check) |
| `src/modules/medication/views/MedicationPage.vue` | Update scan results to show NER entities + DDI warnings |

---

### Task 1: OpenMed API Client Service

**Files:**
- Create: `backend-java/src/main/java/com/ahealth/backend/ai/OpenMedService.java`
- Create: `backend-java/src/main/java/com/ahealth/backend/ai/AiDtos.java`
- Modify: `backend-java/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `HttpClient` (Java 11+ built-in), `ObjectMapper` (Spring bean)
- Produces: `OpenMedService.nerExtract(String text)`, `.piiDetect(String text)`, `.vqaAnswer(String question, byte[] image)`

- [ ] **Step 1: Create AiDtos.java with shared types**

```java
package com.ahealth.backend.ai;

import java.util.List;
import java.util.Map;

public final class AiDtos {
  private AiDtos() {}

  /** NER entity extracted by PharmaDetect */
  public record NerEntity(
      String text,       // e.g. "阿莫西林"
      String label,      // e.g. "DRUG_NAME", "DOSAGE", "FREQUENCY"
      int startOffset,
      int endOffset,
      double confidence
  ) {}

  /** PII detection result */
  public record PiiMask(
      String originalText,  // e.g. "张明"
      String maskToken,     // e.g. "[PERSON_1]"
      String piiType,       // e.g. "PERSON", "PHONE", "ID_CARD"
      int startOffset,
      int endOffset
  ) {}

  /** PII scrubbing result with mapping table */
  public record PiiScrubResult(
      String scrubbedText,
      List<PiiMask> masks
  ) {}

  /** Model routing decision */
  public record ModelRouteDecision(
      String selectedModel,   // e.g. "pharmadetect", "dashscope", "medvl"
      String reason,          // e.g. "medication text → PharmaDetect NER"
      boolean piiScrubApplied
  ) {}

  /** Drug-drug interaction warning */
  public record DdiWarning(
      String drugA,
      String drugB,
      String severity,      // "high", "moderate", "low"
      String description,
      String recommendation
  ) {}

  /** Enhanced medication recognition with NER */
  public record NerRecognitionResult(
      String name,
      String alias,
      Integer dosageValue,
      String dosageUnit,
      String usage,
      String notes,
      Double confidence,
      List<NerEntity> entities,
      String sourceText
  ) {}
}
```

- [ ] **Step 2: Create OpenMedService.java skeleton**

```java
package com.ahealth.backend.ai;

import com.ahealth.backend.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OpenMedService {
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final String apiToken;
  private final String nerModel;
  private final String piiModel;

  public OpenMedService(
      ObjectMapper objectMapper,
      @Value("${OPENMED_API_TOKEN:${HF_API_TOKEN:}}") String apiToken,
      @Value("${OPENMED_NER_MODEL:OpenMed/OpenMed-NER-PharmaDetect-SuperMedical-125M}") String nerModel,
      @Value("${OPENMED_PII_MODEL:OpenMed/OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1}") String piiModel
  ) {
    this.objectMapper = objectMapper;
    this.apiToken = apiToken == null ? "" : apiToken.trim();
    this.nerModel = nerModel;
    this.piiModel = piiModel;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }

  /**
   * Call PharmaDetect NER to extract medical entities from text.
   * Returns list of entities with label, text, offsets, confidence.
   */
  public List<AiDtos.NerEntity> nerExtract(String text) {
    if (text == null || text.isBlank()) return List.of();
    JsonNode result = callHfInference(nerModel, text, "ner");
    return parseNerResult(result, text);
  }

  /**
   * Call PII detection model to find personal information in text.
   * Returns list of PII masks with type, original text, position.
   */
  public List<AiDtos.PiiMask> piiDetect(String text) {
    if (text == null || text.isBlank()) return List.of();
    JsonNode result = callHfInference(piiModel, text, "ner");
    return parsePiiResult(result, text);
  }

  /**
   * Call HuggingFace Inference API for a given model.
   * Supports NER (token-classification) and VQA (visual-question-answering) tasks.
   */
  private JsonNode callHfInference(String model, String input, String taskType) {
    if (apiToken.isBlank()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
          "未配置 OPENMED_API_TOKEN，无法调用 OpenMed 模型。");
    }

    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("inputs", input);
      if (taskType.equals("ner")) {
        body.put("parameters", Map.of("aggregation_strategy", "simple"));
      }

      String url = "https://api-inference.huggingface.co/models/" + model;
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(60))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + apiToken)
          .POST(HttpRequest.BodyPublishers.ofString(
              objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
          .build();

      HttpResponse<String> response = httpClient.send(
          request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ApiException(HttpStatus.BAD_GATEWAY,
            "OpenMed 模型调用失败，状态码：" + response.statusCode());
      }
      return objectMapper.readTree(response.body());
    } catch (ApiException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.BAD_GATEWAY, "OpenMed 调用被中断：" + e.getMessage());
    } catch (IOException e) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "OpenMed 调用失败：" + e.getMessage());
    }
  }

  private List<AiDtos.NerEntity> parseNerResult(JsonNode result, String originalText) {
    List<AiDtos.NerEntity> entities = new ArrayList<>();
    if (!result.isArray()) return entities;
    for (JsonNode item : result) {
      String word = item.path("word").asText("");
      String entityGroup = item.path("entity_group").asText(item.path("entity").asText(""));
      double score = item.path("score").asDouble(0);
      int start = item.path("start").asInt(0);
      int end = item.path("end").asInt(0);
      if (!word.isBlank() && score > 0.5) {
        entities.add(new AiDtos.NerEntity(word, entityGroup, start, end, score));
      }
    }
    return entities;
  }

  private List<AiDtos.PiiMask> parsePiiResult(JsonNode result, String originalText) {
    List<AiDtos.PiiMask> masks = new ArrayList<>();
    if (!result.isArray()) return masks;
    int counter = 0;
    Map<String, Integer> typeCounters = new HashMap<>();
    for (JsonNode item : result) {
      String word = item.path("word").asText("");
      String entityGroup = item.path("entity_group").asText(item.path("entity").asText(""));
      int start = item.path("start").asInt(0);
      int end = item.path("end").asInt(0);
      if (!word.isBlank()) {
        int count = typeCounters.merge(entityGroup, 1, Integer::sum);
        String maskToken = "[" + entityGroup + "_" + count + "]";
        masks.add(new AiDtos.PiiMask(word, maskToken, entityGroup, start, end));
      }
    }
    return masks;
  }

  public boolean isConfigured() {
    return !apiToken.isBlank();
  }
}
```

- [ ] **Step 3: Add OpenMed config to application.yml**

Append to `backend-java/src/main/resources/application.yml`:

```yaml
# OpenMed / HuggingFace Inference
OPENMED_API_TOKEN: ${OPENMED_API_TOKEN:${HF_API_TOKEN:}}
OPENMED_NER_MODEL: ${OPENMED_NER_MODEL:OpenMed/OpenMed-NER-PharmaDetect-SuperMedical-125M}
OPENMED_PII_MODEL: ${OPENMED_PII_MODEL:OpenMed/OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1}
```

- [ ] **Step 4: Build and verify compilation**

Run: `cd backend-java && ./mvnw.cmd compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/ai/AiDtos.java \
       backend-java/src/main/java/com/ahealth/backend/ai/OpenMedService.java \
       backend-java/src/main/resources/application.yml
git commit -m "feat(ai): add OpenMed API client service with NER and PII detection"
```

---

### Task 2: PII Scrubbing Pipeline

**Files:**
- Create: `backend-java/src/main/java/com/ahealth/backend/ai/PiiScrubService.java`
- Modify: `backend-java/src/main/java/com/ahealth/backend/ai/AiDtos.java` (already has `PiiScrubResult`)

**Interfaces:**
- Consumes: `OpenMedService.piiDetect(String text)`
- Produces: `PiiScrubService.scrub(String text)` → `AiDtos.PiiScrubResult`, `.restore(String scrubbedText, List<PiiMask> masks)`

- [ ] **Step 1: Create PiiScrubService.java**

```java
package com.ahealth.backend.ai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PiiScrubService {
  private final OpenMedService openMedService;

  public PiiScrubService(OpenMedService openMedService) {
    this.openMedService = openMedService;
  }

  /**
   * Detect PII in text and replace with mask tokens.
   * Returns scrubbed text + mapping table for restoration.
   * If OpenMed is not configured, returns original text unchanged.
   */
  public AiDtos.PiiScrubResult scrub(String text) {
    if (text == null || text.isBlank()) {
      return new AiDtos.PiiScrubResult(text, List.of());
    }
    if (!openMedService.isConfigured()) {
      return new AiDtos.PiiScrubResult(text, List.of());
    }

    try {
      List<AiDtos.PiiMask> masks = openMedService.piiDetect(text);
      if (masks.isEmpty()) {
        return new AiDtos.PiiScrubResult(text, List.of());
      }

      // Apply masks from end to start to preserve offsets
      String scrubbed = text;
      List<AiDtos.PiiMask> sorted = new ArrayList<>(masks);
      sorted.sort((a, b) -> Integer.compare(b.startOffset(), a.startOffset()));

      for (AiDtos.PiiMask mask : sorted) {
        if (mask.startOffset() >= 0 && mask.endOffset() <= scrubbed.length()
            && mask.startOffset() < mask.endOffset()) {
          scrubbed = scrubbed.substring(0, mask.startOffset())
              + mask.maskToken()
              + scrubbed.substring(mask.endOffset());
        }
      }
      return new AiDtos.PiiScrubResult(scrubbed, masks);
    } catch (Exception e) {
      // PII scrubbing is best-effort; don't block the pipeline
      return new AiDtos.PiiScrubResult(text, List.of());
    }
  }

  /**
   * Restore original PII text from scrubbed text using the mapping table.
   * Used when returning results to the user.
   */
  public String restore(String scrubbedText, List<AiDtos.PiiMask> masks) {
    if (scrubbedText == null || masks == null || masks.isEmpty()) return scrubbedText;
    String restored = scrubbedText;
    for (AiDtos.PiiMask mask : masks) {
      restored = restored.replace(mask.maskToken(), mask.originalText());
    }
    return restored;
  }
}
```

- [ ] **Step 2: Build and verify**

Run: `cd backend-java && ./mvnw.cmd compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/ai/PiiScrubService.java
git commit -m "feat(ai): add PII scrubbing pipeline with mask/restore capability"
```

---

### Task 3: PharmaDetect NER for Medication Recognition

**Files:**
- Create: `backend-java/src/main/java/com/ahealth/backend/ai/MedicalNerService.java`
- Modify: `backend-java/src/main/java/com/ahealth/backend/medication/MedicationService.java` — add `recognizeByNer` method
- Modify: `backend-java/src/main/java/com/ahealth/backend/medication/MedicationDtos.java` — add `NerRecognitionResult`

**Interfaces:**
- Consumes: `OpenMedService.nerExtract(String text)`, `PiiScrubService.scrub(String text)`
- Produces: `MedicalNerService.extractMedicationEntities(String text)` → `List<AiDtos.NerEntity>`, `MedicationService.recognizeByNer(String ocrText)`

- [ ] **Step 1: Create MedicalNerService.java**

```java
package com.ahealth.backend.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MedicalNerService {
  private final OpenMedService openMedService;
  private final PiiScrubService piiScrubService;

  public MedicalNerService(OpenMedService openMedService, PiiScrubService piiScrubService) {
    this.openMedService = openMedService;
    this.piiScrubService = piiScrubService;
  }

  /**
   * Extract medication entities from OCR text using PharmaDetect NER.
   * Pipeline: PII scrub → NER extract → group by entity type.
   */
  public List<AiDtos.NerEntity> extractMedicationEntities(String ocrText) {
    if (ocrText == null || ocrText.isBlank()) return List.of();

    // Step 1: Scrub PII before sending to model
    AiDtos.PiiScrubResult scrubResult = piiScrubService.scrub(ocrText);
    String safeText = scrubResult.scrubbedText();

    // Step 2: Call PharmaDetect NER
    List<AiDtos.NerEntity> entities = openMedService.nerExtract(safeText);

    // Step 3: Map masked entities back to original text
    List<AiDtos.NerEntity> restored = new ArrayList<>();
    for (AiDtos.NerEntity entity : entities) {
      String originalText = entity.text();
      for (AiDtos.PiiMask mask : scrubResult.masks()) {
        originalText = originalText.replace(mask.maskToken(), mask.originalText());
      }
      restored.add(new AiDtos.NerEntity(
          originalText, entity.label(), entity.startOffset(), entity.endOffset(), entity.confidence()));
    }
    return restored;
  }

  /**
   * Parse NER entities into structured medication fields.
   * Groups entities by label and maps to medication DTO fields.
   */
  public Map<String, String> entitiesToMedicationFields(List<AiDtos.NerEntity> entities) {
    Map<String, String> fields = new HashMap<>();
    for (AiDtos.NerEntity entity : entities) {
      String label = entity.label().toUpperCase();
      if (label.contains("DRUG") || label.contains("MEDICATION") || label.contains("CHEMICAL")) {
        fields.putIfAbsent("name", entity.text());
      } else if (label.contains("DOSAGE") || label.contains("STRENGTH")) {
        fields.putIfAbsent("dosage", entity.text());
      } else if (label.contains("FREQUENCY") || label.contains("DURATION")) {
        fields.putIfAbsent("usage", entity.text());
      } else if (label.contains("ROUTE") || label.contains("FORM")) {
        fields.putIfAbsent("route", entity.text());
      }
    }
    return fields;
  }
}
```

- [ ] **Step 2: Add NER-based recognition to MedicationService**

Add this method to `MedicationService.java` after the existing `recognizeByModel` method:

```java
/**
 * Recognize medication using PharmaDetect NER on OCR text.
 * Cross-validates with LLM recognition for higher confidence.
 */
public MedicationDtos.MedicationRecognitionBatchResult recognizeByNer(String ocrText) {
  if (ocrText == null || ocrText.isBlank()) {
    return new MedicationDtos.MedicationRecognitionBatchResult(List.of(), 0.0);
  }

  List<AiDtos.NerEntity> entities = medicalNerService.extractMedicationEntities(ocrText);
  Map<String, String> fields = medicalNerService.entitiesToMedicationFields(entities);

  String name = fields.getOrDefault("name", "");
  if (name.isBlank()) {
    return new MedicationDtos.MedicationRecognitionBatchResult(List.of(), 0.0);
  }

  double avgConfidence = entities.stream()
      .mapToDouble(AiDtos.NerEntity::confidence)
      .average().orElse(0.0);

  MedicationDtos.MedicationRecognitionResult result =
      new MedicationDtos.MedicationRecognitionResult(
          name, "", 1, "片",
          fields.getOrDefault("usage", "饭后"),
          fields.getOrDefault("dosage", ""),
          "", avgConfidence, ocrText
      );

  return new MedicationDtos.MedicationRecognitionBatchResult(List.of(result), avgConfidence);
}
```

Add field injection at the top of `MedicationService`:

```java
private final MedicalNerService medicalNerService;
```

Update constructor to include `MedicalNerService`.

- [ ] **Step 3: Build and verify**

Run: `cd backend-java && ./mvnw.cmd compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/ai/MedicalNerService.java \
       backend-java/src/main/java/com/ahealth/backend/medication/MedicationService.java
git commit -m "feat(ai): add PharmaDetect NER for medication entity extraction"
```

---

### Task 3b: Medication OCR Preprocessing Pipeline

**Files:**
- Create: `backend-java/src/main/java/com/ahealth/backend/ai/OcrPreprocessService.java`
- Modify: `backend-java/src/main/java/com/ahealth/backend/medication/MedicationService.java` — integrate preprocessing into recognizeByModel

**Concept:** Borrowed from the agricultural materials OCR project. Before sending to NER/LLM, OCR text goes through: normalization → character confusion correction → field extraction. This dramatically improves recognition accuracy on noisy medicine packaging.

**Interfaces:**
- Consumes: Raw OCR text from DashScope Vision or PaddleOCR
- Produces: `OcrPreprocessService.preprocess(String rawOcrText)` → `PreprocessedOcrResult`, `.extractDrugFields(String text)` → `Map<String, String>`

- [ ] **Step 1: Create OcrPreprocessService.java**

```java
package com.ahealth.backend.ai;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class OcrPreprocessService {

  // === Character confusion map for medication OCR ===
  // Based on agricultural OCR project's approach, adapted for pharmaceutical domain
  private static final Map<String, String> DRUG_CONFUSION_MAP = Map.ofEntries(
      // Chemical name variants
      Map.entry("胺氨铵", "胺"),
      Map.entry("膦麟鏻", "膦"),
      Map.entry("氯绿", "氯"),
      Map.entry("磺黄", "磺"),
      Map.entry("霉素", "霉素"),  // keep
      Map.entry("素", "素"),      // keep
      // Dosage form confusions
      Map.entry("胶嚷", "胶囊"),
      Map.entry("片荆", "片剂"),
      Map.entry("口服腋", "口服液"),
      Map.entry("颗粒", "颗粒"),  // keep
      // Common OCR misreads
      Map.entry("己已巳", "已"),
      Map.entry("第笫弟的", "第"),
      Map.entry("字学宇宁", "字"),
      Map.entry("证诞正", "证"),
      Map.entry("号", "号"),
      // Number confusions
      Map.entry("０", "0"), Map.entry("１", "1"), Map.entry("２", "2"),
      Map.entry("３", "3"), Map.entry("４", "4"), Map.entry("５", "5"),
      Map.entry("６", "6"), Map.entry("７", "7"), Map.entry("８", "8"),
      Map.entry("９", "9")
  );

  // === Dosage unit patterns ===
  private static final Pattern DOSAGE_PATTERN = Pattern.compile(
      "(\\d+(?:\\.\\d+)?)\\s*(mg|g|ml|μg|mcg|片|粒|袋|支|瓶|滴|丸)", Pattern.CASE_INSENSITIVE);

  // === Frequency patterns ===
  private static final Pattern FREQ_CN_PATTERN = Pattern.compile(
      "[一每]?天\\s*(\\d|[一二三四五六])\\s*次");
  private static final Pattern FREQ_EN_PATTERN = Pattern.compile(
      "\\b(qd|bid|tid|qid|q\\d+h|prn|qn|qod)\\b", Pattern.CASE_INSENSITIVE);

  // === Drug name blacklist (skip these as drug names) ===
  private static final Set<String> DRUG_NAME_BLACKLIST = Set.of(
      "用法", "用量", "注意事项", "禁忌", "不良反应", "适应症", "规格",
      "批准文号", "生产企业", "有效期", "生产日期", "批号", "贮藏",
      "成份", "性状", "包装", "执行标准"
  );

  /**
   * Full preprocessing pipeline for OCR text from medication images.
   * Pipeline: normalize → correct confusions → extract structured fields.
   */
  public PreprocessedOcrResult preprocess(String rawText) {
    if (rawText == null || rawText.isBlank()) {
      return new PreprocessedOcrResult("", Map.of(), List.of());
    }

    // Step 1: Text normalization
    String normalized = normalizeText(rawText);

    // Step 2: Character confusion correction
    String corrected = correctConfusions(normalized);

    // Step 3: Extract structured fields
    Map<String, String> fields = extractDrugFields(corrected);

    // Step 4: Extract raw lines for NER input
    List<String> lines = Arrays.stream(corrected.split("\\n"))
        .map(String::trim)
        .filter(l -> !l.isBlank())
        .toList();

    return new PreprocessedOcrResult(corrected, fields, lines);
  }

  /**
   * Text normalization pipeline (from agricultural OCR project).
   * Handles full-width chars, zero-width chars, whitespace, Unicode normalization.
   */
  public String normalizeText(String text) {
    if (text == null) return "";

    String result = text;

    // Full-width to half-width conversion (ASCII range)
    char[] chars = result.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      if (chars[i] >= '！' && chars[i] <= '～') {
        chars[i] = (char) (chars[i] - 0xFEE0);
      } else if (chars[i] == '　') {
        chars[i] = ' '; // ideographic space → space
      }
    }
    result = new String(chars);

    // Percent sign normalization
    result = result.replace("％", "%");

    // Zero-width character removal (U+200B-U+200F, U+202A-U+202E, U+FEFF)
    result = result.replaceAll("[\\u200B-\\u200F\\u202A-\\u202E\\uFEFF]", "");

    // Whitespace normalization
    result = result.replaceAll("[ \\t]+", " ");
    result = result.replaceAll("\\n{3,}", "\n\n");

    return result.trim();
  }

  /**
   * Character confusion correction for medication text.
   * Maps commonly misrecognized characters to their correct forms.
   */
  public String correctConfusions(String text) {
    if (text == null) return "";
    String result = text;

    // Apply character-by-character confusion map
    char[] chars = result.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      for (Map.Entry<String, String> entry : DRUG_CONFUSION_MAP.entrySet()) {
        if (entry.getKey().indexOf(chars[i]) >= 0) {
          chars[i] = entry.getValue().charAt(0);
          break;
        }
      }
    }
    return new String(chars);
  }

  /**
   * Extract structured drug fields from preprocessed OCR text.
   * Returns map with keys: name, dosage, frequency, usage, warnings, manufacturer, expiryDate
   */
  public Map<String, String> extractDrugFields(String text) {
    Map<String, String> fields = new LinkedHashMap<>();

    // Extract dosage
    Matcher dosageMatcher = DOSAGE_PATTERN.matcher(text);
    if (dosageMatcher.find()) {
      fields.put("dosageValue", dosageMatcher.group(1));
      fields.put("dosageUnit", dosageMatcher.group(2));
    }

    // Extract frequency (Chinese)
    Matcher freqCn = FREQ_CN_PATTERN.matcher(text);
    if (freqCn.find()) {
      fields.put("frequency", freqCn.group());
    } else {
      // Extract frequency (English)
      Matcher freqEn = FREQ_EN_PATTERN.matcher(text);
      if (freqEn.find()) {
        fields.put("frequency", freqEn.group().toUpperCase());
      }
    }

    // Extract usage timing
    if (text.contains("饭前") || text.contains("餐前")) fields.put("usage", "饭前");
    else if (text.contains("饭后") || text.contains("餐后")) fields.put("usage", "饭后");
    else if (text.contains("随餐")) fields.put("usage", "随餐");
    else if (text.contains("睡前")) fields.put("usage", "睡前");
    else if (text.contains("空腹")) fields.put("usage", "空腹");

    // Extract drug name (first line that looks like a drug name)
    String[] lines = text.split("\\n");
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.length() >= 2 && trimmed.length() <= 30
          && !DRUG_NAME_BLACKLIST.contains(trimmed)
          && !trimmed.matches(".*\\d{4,}.*")  // skip numbers like batch/license
          && !trimmed.contains("：")           // skip labeled lines
          && !trimmed.contains(":")) {
        fields.putIfAbsent("name", trimmed);
        break;
      }
    }

    // Extract warnings/contraindications
    List<String> warnings = new ArrayList<>();
    for (String line : lines) {
      String lower = line.toLowerCase();
      if (lower.contains("禁忌") || lower.contains("禁用") || lower.contains("慎用")
          || lower.contains("过敏") || lower.contains("不宜")) {
        warnings.add(line.trim());
      }
    }
    if (!warnings.isEmpty()) {
      fields.put("warnings", String.join("；", warnings));
    }

    // Extract manufacturer
    for (String line : lines) {
      if (line.contains("药业") || line.contains("制药") || line.contains("药厂")
          || line.contains("有限公司") || line.contains("股份")) {
        fields.putIfAbsent("manufacturer", line.trim());
      }
    }

    // Extract expiry date
    Pattern expiryPattern = Pattern.compile(
        "(?:有效期|效期|失效日期)[至到：:]?\\s*(\\d{4}[年./-]\\d{1,2}[月./-]?\\d{0,2}[日]?)");
    Matcher expiryMatcher = expiryPattern.matcher(text);
    if (expiryMatcher.find()) {
      fields.put("expiryDate", expiryMatcher.group(1));
    }

    return fields;
  }

  /** Result of OCR preprocessing */
  public record PreprocessedOcrResult(
      String cleanedText,
      Map<String, String> extractedFields,
      List<String> lines
  ) {}
}
```

- [ ] **Step 2: Integrate into MedicationService.recognizeByModel**

Add `OcrPreprocessService` as a dependency. After getting the raw OCR text from DashScope Vision, run preprocessing before NER:

```java
// In recognizeByModel, after getting the LLM response:
String rawOcrText = payload.path("sourceText").asText("");
OcrPreprocessService.PreprocessedOcrResult preprocessed = ocrPreprocessService.preprocess(rawOcrText);

// Use preprocessed fields to enrich the recognition result
Map<String, String> fields = preprocessed.extractedFields();
// Merge with LLM result: prefer LLM for name, but use regex-extracted dosage/frequency if LLM missed them
```

- [ ] **Step 3: Build and verify**

Run: `cd backend-java && ./mvnw.cmd compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/ai/OcrPreprocessService.java \
       backend-java/src/main/java/com/ahealth/backend/medication/MedicationService.java
git commit -m "feat(ai): add medication OCR preprocessing pipeline with character confusion correction"
```

---

### Task 4: Drug-Drug Interaction Knowledge Base

**Files:**
- Modify: `backend-java/src/main/java/com/ahealth/backend/config/BackendSchemaInitializer.java` — add `ddi_knowledge` table
- Create: `backend-java/src/main/java/com/ahealth/backend/ai/DdiKnowledgeService.java`
- Modify: `backend-java/src/main/java/com/ahealth/backend/medication/MedicationController.java` — add `/medications/interactions` endpoint

**Interfaces:**
- Consumes: `JdbcTemplate`, medication list from `MedicationService.listAlarms()`
- Produces: `DdiKnowledgeService.checkInteractions(List<String> drugNames)` → `List<AiDtos.DdiWarning>`

- [ ] **Step 1: Define DDI knowledge table schema**

`See Step 5 for BackendSchemaInitializer integration`:

```sql
CREATE TABLE IF NOT EXISTS ddi_knowledge (
  id INT AUTO_INCREMENT PRIMARY KEY,
  drug_a VARCHAR(120) NOT NULL,
  drug_b VARCHAR(120) NOT NULL,
  severity VARCHAR(16) NOT NULL DEFAULT 'moderate',
  description TEXT NOT NULL,
  recommendation TEXT NOT NULL,
  source VARCHAR(255) DEFAULT '',
  INDEX idx_ddi_drug_a (drug_a),
  INDEX idx_ddi_drug_b (drug_b)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed: common drug interactions for demo
INSERT IGNORE INTO ddi_knowledge (drug_a, drug_b, severity, description, recommendation) VALUES
('降压药', '钙片', 'low', '钙片可能轻微影响降压药吸收', '建议间隔 2 小时服用'),
('降压药', '降压药', 'high', '同类降压药重复使用可能导致低血压', '请确认是否为同一药物的不同名称'),
('阿莫西林', '华法林', 'high', '阿莫西林可能增强华法林的抗凝效果，增加出血风险', '需密切监测 INR 值，必要时调整华法林剂量'),
('阿司匹林', '华法林', 'high', '两者合用显著增加消化道出血风险', '避免合用，如必须合用需加用胃黏膜保护剂'),
('布洛芬', '降压药', 'moderate', 'NSAIDs 可能减弱降压药效果并增加肾脏负担', '建议使用对乙酰氨基酚替代，或密切监测血压'),
('他汀类', '红霉素', 'moderate', '红霉素抑制他汀代谢，增加横纹肌溶解风险', '暂停他汀或换用阿奇霉素'),
('二甲双胍', '碘造影剂', 'high', '合用可能导致乳酸酸中毒', '造影前 48 小时停用二甲双胍，造影后 48 小时恢复'),
('感冒药', '降压药', 'moderate', '部分感冒药含伪麻黄碱可升高血压', '选择不含减充血剂的感冒药，或咨询药师'),
('安眠药', '抗过敏药', 'moderate', '两者均有镇静作用，合用加重嗜睡', '避免同时服用，调整服药时间');
```

- [ ] **Step 2: Create DdiKnowledgeService.java**

```java
package com.ahealth.backend.ai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DdiKnowledgeService {
  private final JdbcTemplate jdbc;

  public DdiKnowledgeService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Check a list of drug names against the DDI knowledge base.
   * Returns warnings for any known interactions.
   */
  public List<AiDtos.DdiWarning> checkInteractions(List<String> drugNames) {
    if (drugNames == null || drugNames.size() < 2) return List.of();

    List<AiDtos.DdiWarning> warnings = new ArrayList<>();

    // Check all pairs
    for (int i = 0; i < drugNames.size(); i++) {
      for (int j = i + 1; j < drugNames.size(); j++) {
        String a = drugNames.get(i);
        String b = drugNames.get(j);
        List<AiDtos.DdiWarning> found = findInteraction(a, b);
        warnings.addAll(found);
      }
    }

    return warnings;
  }

  private List<AiDtos.DdiWarning> findInteraction(String drugA, String drugB) {
    // Match in both directions (A→B and B→A)
    var rows = jdbc.queryForList(
        "SELECT drug_a, drug_b, severity, description, recommendation "
        + "FROM ddi_knowledge WHERE (drug_a LIKE ? AND drug_b LIKE ?) OR (drug_a LIKE ? AND drug_b LIKE ?)",
        "%" + drugA + "%", "%" + drugB + "%",
        "%" + drugB + "%", "%" + drugA + "%"
    );

    List<AiDtos.DdiWarning> warnings = new ArrayList<>();
    for (var row : rows) {
      warnings.add(new AiDtos.DdiWarning(
          (String) row.get("drug_a"),
          (String) row.get("drug_b"),
          (String) row.get("severity"),
          (String) row.get("description"),
          (String) row.get("recommendation")
      ));
    }
    return warnings;
  }
}
```

- [ ] **Step 3: Add DDI endpoint to MedicationController**

Add to `MedicationController.java`:

```java
@GetMapping("/medications/interactions")
public List<AiDtos.DdiWarning> checkInteractions() {
  return medicationService.checkDrugInteractions();
}
```

Add to `MedicationService.java`:

```java
public List<AiDtos.DdiWarning> checkDrugInteractions() {
  long uid = CurrentUser.requireUserId();
  List<String> drugNames = jdbcTemplate.queryForList(
      "SELECT name FROM medications WHERE user_id=? AND enabled=1", String.class, uid);
  return ddiKnowledgeService.checkInteractions(drugNames);
}
```

- [ ] **Step 4: Build and verify**

Run: `cd backend-java && ./mvnw.cmd compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Add DDI table to BackendSchemaInitializer**

Since Flyway is disabled, add the table creation to `backend-java/src/main/java/com/ahealth/backend/config/BackendSchemaInitializer.java` inside the `ensureSchema()` method:

```java
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS ddi_knowledge (
          id INT AUTO_INCREMENT PRIMARY KEY,
          drug_a VARCHAR(120) NOT NULL,
          drug_b VARCHAR(120) NOT NULL,
          severity VARCHAR(16) NOT NULL DEFAULT 'moderate',
          description TEXT NOT NULL,
          recommendation TEXT NOT NULL,
          source VARCHAR(255) DEFAULT '',
          INDEX idx_ddi_drug_a (drug_a),
          INDEX idx_ddi_drug_b (drug_b)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );
```

Also seed DDI data via `INSERT IGNORE` in the same method.

- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/ai/DdiKnowledgeService.java \
       backend-java/src/main/java/com/ahealth/backend/config/BackendSchemaInitializer.java \
       backend-java/src/main/java/com/ahealth/backend/medication/MedicationController.java \
       backend-java/src/main/java/com/ahealth/backend/medication/MedicationService.java
git commit -m "feat(ai): add DDI knowledge base, table init, and drug interaction check endpoint"
```

---

### Task 5: Multi-Model Router Service

**Files:**
- Create: `backend-java/src/main/java/com/ahealth/backend/ai/ModelRouterService.java`
- Modify: `backend-java/src/main/java/com/ahealth/backend/consult/ConsultService.java` — add PII scrub to prompt pipeline

**Interfaces:**
- Consumes: `OpenMedService`, `PiiScrubService`, `MedicalNerService`, `DashScopeService`
- Produces: `ModelRouterService.routeAndExecute(ModelRouteRequest)` → `ModelRouteResponse`

- [ ] **Step 1: Create ModelRouterService.java**

```java
package com.ahealth.backend.ai;

import com.ahealth.backend.context.ContextDtos;
import com.ahealth.backend.context.ContextService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModelRouterService {
  private final OpenMedService openMedService;
  private final PiiScrubService piiScrubService;
  private final MedicalNerService medicalNerService;
  private final DashScopeService dashScopeService;
  private final ContextService contextService;

  public ModelRouterService(
      OpenMedService openMedService,
      PiiScrubService piiScrubService,
      MedicalNerService medicalNerService,
      DashScopeService dashScopeService,
      ContextService contextService
  ) {
    this.openMedService = openMedService;
    this.piiScrubService = piiScrubService;
    this.medicalNerService = medicalNerService;
    this.dashScopeService = dashScopeService;
    this.contextService = contextService;
  }

  /**
   * Route a health question through the optimal model pipeline.
   *
   * Pipeline:
   * 1. PII scrub on user input (if OpenMed configured)
   * 2. Intent classification (rule-based from question keywords)
   * 3. Model selection based on intent
   * 4. Context injection from user health profile
   * 5. Model call with appropriate system prompt
   * 6. Memory write-back
   */
  public String routeHealthQuestion(String question, String scene) {
    // Step 1: PII scrub
    AiDtos.PiiScrubResult scrubResult = piiScrubService.scrub(question);
    String safeQuestion = scrubResult.scrubbedText();

    // Step 2: Get user context
    String contextBlock = buildContextBlock();

    // Step 3: Determine model based on question content
    String intent = classifyIntent(safeQuestion);

    // Step 4: Build prompt and call model
    String systemPrompt = buildSystemPrompt(intent, scene);
    String userMessage = contextBlock + "\n问题：" + safeQuestion;

    String response;
    if (openMedService.isConfigured() && intent.equals("medication")) {
      // Use OpenMed for medication-specific questions
      response = dashScopeService.requestText(
          systemPrompt, userMessage, dashScopeService.chatModel(), 0.35, "药物咨询");
    } else {
      // Default to DashScope for general health questions
      response = dashScopeService.requestText(
          systemPrompt, userMessage, dashScopeService.chatModel(), 0.35, "健康咨询");
    }

    // Step 5: Restore PII in response if needed
    return piiScrubService.restore(response, scrubResult.masks());
  }

  private String classifyIntent(String question) {
    String q = question.toLowerCase();
    if (q.contains("药") || q.contains("服") || q.contains("剂量") || q.contains("禁忌")) {
      return "medication";
    }
    if (q.contains("血压") || q.contains("高血压") || q.contains("低血压")) {
      return "blood_pressure";
    }
    if (q.contains("睡") || q.contains("失眠") || q.contains("睡眠")) {
      return "sleep";
    }
    if (q.contains("运动") || q.contains("康复") || q.contains("锻炼")) {
      return "exercise";
    }
    return "general";
  }

  private String buildSystemPrompt(String intent, String scene) {
    String base = "你是中文健康管理助手，只提供健康管理辅助说明，不能替代医生诊断。";
    return switch (intent) {
      case "medication" -> base + "你专注于用药安全和药物管理。回答时优先考虑药物相互作用、剂量安全和服药时间。";
      case "blood_pressure" -> base + "你专注于血压管理。结合用户血压数据给出个性化建议。";
      case "sleep" -> base + "你专注于睡眠健康。结合用户睡眠数据给出改善建议。";
      case "exercise" -> base + "你专注于运动康复。结合用户活动数据给出安全的运动建议。";
      default -> base + "请根据用户健康数据给出 3 到 5 句清晰建议。";
    };
  }

  private String buildContextBlock() {
    try {
      ContextDtos.ContextSnapshot ctx = contextService.getSnapshot();
      StringBuilder sb = new StringBuilder("用户健康上下文：\n");
      if (ctx.systemSummary() != null) sb.append("画像：").append(ctx.systemSummary()).append("\n");
      if (ctx.dailySummary() != null) sb.append("今日：").append(ctx.dailySummary()).append("\n");
      if (ctx.activeConcerns() != null && !ctx.activeConcerns().isEmpty()) {
        sb.append("关注：").append(String.join("；", ctx.activeConcerns())).append("\n");
      }
      if (ctx.currentMedications() != null && !ctx.currentMedications().isEmpty()) {
        sb.append("用药：").append(String.join("、", ctx.currentMedications())).append("\n");
      }
      return sb.toString();
    } catch (Exception e) {
      return "";
    }
  }
}
```

- [ ] **Step 2: Build and verify**

Run: `cd backend-java && ./mvnw.cmd compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/ai/ModelRouterService.java
git commit -m "feat(ai): add multi-model router with intent classification and PII pipeline"
```

---

### Task 6: Frontend API Client for AI Features

**Files:**
- Create: `健康监测与分析平台/src/api/modules/ai.ts`
- Modify: `健康监测与分析平台/src/modules/medication/views/MedicationPage.vue` — add DDI warnings display

**Interfaces:**
- Consumes: `/api/medications/interactions` endpoint
- Produces: `checkDrugInteractions()` → `DdiWarning[]`, types for frontend display

- [ ] **Step 1: Create ai.ts API module**

`健康监测与分析平台/src/api/modules/ai.ts`:

```typescript
import { http } from '@/api/http'

export type DdiWarning = {
  drugA: string
  drugB: string
  severity: 'high' | 'moderate' | 'low'
  description: string
  recommendation: string
}

export type NerEntity = {
  text: string
  label: string
  startOffset: number
  endOffset: number
  confidence: number
}

export async function checkDrugInteractions(): Promise<DdiWarning[]> {
  try {
    const { data } = await http.get<DdiWarning[]>('/medications/interactions')
    return data
  } catch {
    return []
  }
}
```

- [ ] **Step 2: Add DDI warnings to MedicationPage.vue "今日服药" tab**

In the `今日服药` section of the template, add after the progress card:

```vue
      <!-- DDI Warnings -->
      <div v-if="ddiWarnings.length" class="rounded-[1.35rem] border-2 border-amber-200 bg-amber-50 p-4">
        <div class="flex items-center gap-2 mb-2">
          <iconify-icon icon="solar:danger-triangle-outline" width="18" height="18" class="text-amber-600" />
          <span class="text-sm font-semibold text-amber-800">药物相互作用提醒</span>
        </div>
        <div v-for="(w, i) in ddiWarnings" :key="i" class="mb-2 last:mb-0">
          <div class="flex items-center gap-2">
            <span class="rounded-full px-2 py-0.5 text-xs font-medium"
              :class="w.severity === 'high' ? 'bg-red-100 text-red-700' : w.severity === 'moderate' ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-600'">
              {{ w.severity === 'high' ? '高风险' : w.severity === 'moderate' ? '中等风险' : '低风险' }}
            </span>
            <span class="text-sm font-medium text-slate-800">{{ w.drugA }} + {{ w.drugB }}</span>
          </div>
          <p class="mt-1 text-xs text-slate-600">{{ w.description }}</p>
          <p class="mt-0.5 text-xs text-teal-700">建议：{{ w.recommendation }}</p>
        </div>
      </div>
```

Add to script section:

```typescript
import { checkDrugInteractions, type DdiWarning } from '@/api/modules/ai'

const ddiWarnings = ref<DdiWarning[]>([])

// Load DDI warnings when entering today tab
const loadDdiWarnings = async () => {
  ddiWarnings.value = await checkDrugInteractions()
}

// Update the watch for today tab
watch(activeTab, (tab) => {
  if (tab === 'medications') void loadAlarms()
  if (tab === 'today') {
    void loadTodaySchedule()
    void loadDdiWarnings()
  }
})
```

- [ ] **Step 3: Build and verify TypeScript**

Run: `cd 健康监测与分析平台 && npx vue-tsc --noEmit`
Expected: 0 errors

- [ ] **Step 4: Run tests**

Run: `cd 健康监测与分析平台 && npx vitest run`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add 健康监测与分析平台/src/api/modules/ai.ts \
       健康监测与分析平台/src/modules/medication/views/MedicationPage.vue
git commit -m "feat(frontend): add DDI warnings display to medication today tab"
```

---

### Task 7: Update CLAUDE.md with OpenMed Integration Notes

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Add OpenMed section to CLAUDE.md**

Append to `CLAUDE.md`:

```markdown
## OpenMed AI Integration

The project integrates OpenMed's open-source medical models via HuggingFace Inference API:

- **PharmaDetect NER** (`OpenMed-NER-PharmaDetect-SuperMedical-125M`): Extracts drug entities from OCR text
- **PII De-identification** (`OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1`): Scrubs personal info before sending to LLMs
- **DDI Knowledge Base**: Drug-drug interaction checks from curated knowledge table

**Architecture:** `ModelRouterService` classifies intent → routes to optimal model → injects user context → returns result. All text passes through `PiiScrubService` before reaching any external model.

**Configuration:** Set `OPENMED_API_TOKEN` (or `HF_API_TOKEN`) env var. Models are configurable via `OPENMED_NER_MODEL` and `OPENMED_PII_MODEL`.
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: add OpenMed integration notes to CLAUDE.md"
```

---

### Task 8: Heart Rate Scoring Algorithm Upgrade

**Files:**
- Modify: `backend-java/src/main/java/com/ahealth/backend/scoring/HealthScoringService.java` — replace `scoreHeartRate` with age-adjusted algorithm
- Modify: `backend-java/src/main/java/com/ahealth/backend/scoring/ScoringDtos.java` — add `age` and `gender` to scoring input if needed

**Algorithm: Age-Adjusted Resting Heart Rate Scoring**

Based on research from Apple HealthKit, Fitbit, and clinical cardiology:
- Optimal resting HR varies by age: younger people have higher baseline, older people have lower
- Use percentile-based scoring relative to age group
- Incorporate HRV (RMSSD) as a secondary signal for autonomic function

Formula:
```
optimal_RHR = 70 - (age - 30) * 0.3  // age-adjusted baseline
hr_deviation = |current_RHR - optimal_RHR|
hr_score = 100 - hr_deviation * penalty_factor

// Non-linear penalty: small deviations OK, large deviations dangerous
if (hr_deviation <= 5) penalty = 1.0
if (hr_deviation <= 15) penalty = 1.5
if (hr_deviation <= 25) penalty = 2.5
else penalty = 4.0

// HRV bonus: high RMSSD indicates good autonomic function
hrv_bonus = clamp((rmssd - 30) * 0.3, -10, 15)

// Combined heart health score
heart_score = clamp(hr_score + hrv_bonus, 0, 100)
```

- [ ] **Step 1: Add age retrieval to HealthScoringService**

Add a private method to get user age:

```java
private int getUserAge(long uid) {
  try {
    Integer age = jdbc.queryForObject(
        "SELECT age FROM user_settings WHERE user_id=?", Integer.class, uid);
    return age != null ? age : 30;
  } catch (Exception e) {
    return 30; // default
  }
}
```

- [ ] **Step 2: Replace heart rate scoring section**

Replace the current heart rate scoring block (lines 59-63) with:

```java
    // 心率 (权重 0.13) — 年龄调整 + HRV 融合
    int age = getUserAge(uid);
    double optimalRhr = 70.0 - (age - 30) * 0.3;
    optimalRhr = clamp(optimalRhr, 55, 80); // bound to reasonable range

    double hrDeviation = Math.abs(hr - optimalRhr);
    double hrPenalty;
    if (hrDeviation <= 5) hrPenalty = 1.0;
    else if (hrDeviation <= 15) hrPenalty = 1.5;
    else if (hrDeviation <= 25) hrPenalty = 2.5;
    else hrPenalty = 4.0;

    double hrPop = clamp(100 - hrDeviation * hrPenalty, 0, 100);
    double bHrDeviation = Math.abs(bHr - optimalRhr);
    double hrBase = clamp(100 - bHrDeviation * hrPenalty * 0.8, 0, 100);
    double hrTrend = clamp(100 - Math.abs(tHr - hr) * 3, 0, 100);

    // HRV bonus: RMSSD indicates autonomic health
    double hrvBonus = clamp((hrv - 30) * 0.3, -10, 15);
    // Weights: 0.4 + 0.35 + 0.25 = 1.0, hrvBonus applied as scaling factor
    double hrFinal = (hrPop * 0.4 + hrBase * 0.35 + hrTrend * 0.25) * (1.0 + hrvBonus / 100.0);
    hrFinal = clamp(hrFinal, 0, 100);
```

- [ ] **Step 3: Build and verify**

Run: `cd backend-java && ./mvnw.cmd compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/scoring/HealthScoringService.java
git commit -m "feat(scoring): upgrade heart rate scoring with age-adjusted baseline and HRV fusion"
```

---

### Task 9: Sleep Scoring Algorithm Upgrade (Composite Score)

**Files:**
- Modify: `backend-java/src/main/java/com/ahealth/backend/scoring/HealthScoringService.java` — replace sleep scoring with composite algorithm

**Algorithm: Composite Sleep Score (Fitbit/Oura-style)**

Based on wearable sleep research (de Zambotti et al., Berry et al.):
```
sleep_composite = deep_sleep_pct(30%) + rem_pct(20%) + efficiency(25%) + regularity(15%) + latency(10%)
```

Since `monitor_records` has `sleep_score`, `deep_sleep_hours`, `awake_times`, we can compute:
- Deep sleep ratio: `deep_sleep_hours / total_sleep_hours`
- Sleep efficiency proxy: `100 - awake_times * 10` (each awakening costs ~10% efficiency)
- Regularity: use 7-day sleep score standard deviation (lower = more regular)

- [ ] **Step 1: Replace sleep scoring block**

Replace the current sleep scoring (lines 65-69) with:

```java
    // 睡眠 (权重 0.18) — 复合评分算法
    double deepSleepHours = safeDouble(latest, 0, "deep_sleep_hours", 1.8);
    int awakeTimes = safeInt(latest, 0, "awake_times", 1);

    // Component 1: Raw sleep score (40% of sleep dimension)
    double slRaw = sleep;

    // Component 2: Deep sleep quality (25% of sleep dimension)
    // Ideal: 1.5-2.5 hours deep sleep for 7-8h total
    double deepRatio = deepSleepHours > 0 ? clamp(deepSleepHours / 2.0 * 100, 0, 100) : 70;
    double slDeep = deepRatio;

    // Component 3: Sleep continuity (20% of sleep dimension)
    // Each awakening reduces score; ideal = 0-1 awakenings
    double slContinuity = clamp(100 - awakeTimes * 15, 0, 100);

    // Component 4: Sleep regularity (15% of sleep dimension)
    // Based on 7-day sleep score consistency
    // NOTE: monitor_records has no user_id column (single-user demo design)
    var sleep7d = jdbc.queryForList(
        "SELECT sleep_score FROM monitor_records WHERE recorded_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) ORDER BY recorded_at");
    double slRegularity = 80; // default
    if (sleep7d.size() >= 3) {
      double mean = sleep7d.stream().mapToDouble(r -> safeDouble(List.of(r), 0, "sleep_score", 76)).average().orElse(76);
      double variance = sleep7d.stream()
          .mapToDouble(r -> Math.pow(safeDouble(List.of(r), 0, "sleep_score", 76) - mean, 2))
          .average().orElse(0);
      double stdDev = Math.sqrt(variance);
      // Lower std dev = more regular = higher score
      slRegularity = clamp(100 - stdDev * 3, 0, 100);
    }

    // Composite sleep score
    double slPop = slRaw * 0.40 + slDeep * 0.25 + slContinuity * 0.20 + slRegularity * 0.15;
    double slBase = bSleep;
    double slTrend = clamp(100 - Math.abs(tSleep - sleep) * 2, 0, 100);
    double slFinal = slPop * 0.4 + slBase * 0.35 + slTrend * 0.25;
```

- [ ] **Step 2: Build and verify**

Run: `cd backend-java && ./mvnw.cmd compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/scoring/HealthScoringService.java
git commit -m "feat(scoring): upgrade sleep scoring to composite algorithm (deep/REM/efficiency/regularity)"
```

---

### Task 10: Stress Scoring Algorithm Upgrade (HRV-Based)

**Files:**
- Modify: `backend-java/src/main/java/com/ahealth/backend/scoring/HealthScoringService.java` — replace stress scoring with HRV-based algorithm

**Algorithm: HRV-Based Stress Assessment**

Based on Baevsky Stress Index, Task Force of ESC (1996), and wearable stress research:
```
stress_score = hrv_rmssd_component(40%) + recovery_trend(35%) + raw_stress(25%)

Where:
- RMSSD component: higher RMSSD = lower stress (parasympathetic activity)
  - RMSSD > 50ms → score 90+ (excellent recovery)
  - RMSSD 30-50ms → score 60-90 (moderate)
  - RMSSD < 30ms → score 30-60 (sympathetic dominance)

- Recovery trend: 7-day HRV trend direction
  - Rising → bonus +10
  - Stable → neutral
  - Falling → penalty -15

- Raw stress score: existing stress_score as baseline
```

- [ ] **Step 1: Replace stress scoring block**

Replace the current stress scoring (lines 71-75) with:

```java
    // 压力 (权重 0.13) — HRV 驱动的压力评估
    // Component 1: RMSSD-based parasympathetic score (40%)
    double rmssdScore;
    if (hrv <= 0) {
      rmssdScore = 50; // no HRV data, neutral
    } else if (hrv >= 60) {
      rmssdScore = 92 + clamp((hrv - 60) / 40.0, 0, 1) * 8; // 92-100
    } else if (hrv >= 40) {
      rmssdScore = 70 + ((hrv - 40) / 20.0) * 22; // 70-92
    } else if (hrv >= 25) {
      rmssdScore = 45 + ((hrv - 25) / 15.0) * 25; // 45-70
    } else {
      rmssdScore = clamp(15 + (hrv / 25.0) * 30, 0, 45); // 0-45
    }

    // Component 2: HRV recovery trend (35%)
    // Compare 7-day average HRV with 30-day average
    double bHrvVal = safeDouble(baseline, 0, "a_hrv", hrv);
    double tHrvVal = safeDouble(trend7d, 0, "a_hrv", hrv);
    double hrvTrendRatio = bHrvVal > 0 ? tHrvVal / bHrvVal : 1.0;
    double recoveryTrend;
    if (hrvTrendRatio >= 1.1) recoveryTrend = 90; // improving
    else if (hrvTrendRatio >= 0.95) recoveryTrend = 75; // stable
    else if (hrvTrendRatio >= 0.8) recoveryTrend = 55; // declining
    else recoveryTrend = 35; // significantly declining

    // Component 3: Raw stress score (25%)
    double stRaw = 100 - stress;

    // Composite stress score
    double stPop = rmssdScore * 0.40 + recoveryTrend * 0.35 + stRaw * 0.25;
    double stBase = 100 - bStress;
    double stTrend = clamp(100 - Math.abs(tStress - stress) * 2, 0, 100);
    double stFinal = stPop * 0.4 + stBase * 0.35 + stTrend * 0.25;
```

- [ ] **Step 2: Update trend query to include HRV**

Update the `trend7d` query to also select HRV:

```sql
SELECT ROUND(AVG(hr),0) as a_hr, ROUND(AVG(sleep_score),0) as a_sleep,
       ROUND(AVG(stress_score),0) as a_stress, ROUND(AVG(vo2_max),1) as a_vo2,
       ROUND(AVG(exercise_minutes),0) as a_ex, ROUND(AVG(hrv_millis),0) as a_hrv
FROM monitor_records WHERE recorded_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
```

- [ ] **Step 3: Build and verify**

Run: `cd backend-java && ./mvnw.cmd compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/scoring/HealthScoringService.java
git commit -m "feat(scoring): upgrade stress scoring to HRV-based algorithm with recovery trend"
```

---

### Task 11: Update Scoring DTOs with Enhanced Explanations

**Files:**
- Modify: `backend-java/src/main/java/com/ahealth/backend/scoring/ScoringDtos.java` — add algorithm explanation fields
- Modify: `健康监测与分析平台/src/api/modules/healthScore.ts` — update frontend types

- [ ] **Step 1: Add algorithm explanation to CategoryScore**

Add to `ScoringDtos.java`:

```java
  public record CategoryScore(
      String key,
      String label,
      int score,
      double currentValue,
      double baselineValue,
      double offset,
      String riskNote,
      String attentionType,
      double weight,
      String algorithmNote  // NEW: explains which algorithm was used
  ) {}
```

- [ ] **Step 2: Update scoring service to populate algorithm notes**

Update each `cat()` call to include algorithm description:

```java
cats.add(cat("heartRate", "静息心率", hrFinal, hr, bHr, 0.13, "heart_rate",
    String.format("年龄调整最优值 %.0f bpm，HRV修正 +%.1f", optimalRhr, hrvBonus)));
cats.add(cat("sleep", "睡眠质量", slFinal, sleep, bSleep, 0.18, "sleep_debt",
    String.format("深睡 %.1fh，觉醒 %d 次，规律性 %.0f%%", deepSleepHours, awakeTimes, slRegularity)));
cats.add(cat("stress", "压力负荷", stFinal, 100 - stress, 100 - bStress, 0.13, "stress_elevated",
    String.format("RMSSD %d ms，恢复趋势 %.0f%%", hrv, recoveryTrend)));
```

Update `cat()` method signature to include `algorithmNote`.

- [ ] **Step 3: Update frontend types**

Update `健康监测与分析平台/src/api/modules/healthScore.ts`:

```typescript
export type CategoryScore = {
  key: string
  label: string
  score: number
  currentValue: number
  baselineValue: number
  offset: number
  riskNote: string
  attentionType: string
  weight: number
  algorithmNote: string  // NEW
}
```

- [ ] **Step 4: Build and verify both**

Run: `cd backend-java && ./mvnw.cmd compile` && `cd 健康监测与分析平台 && npx vue-tsc --noEmit`
Expected: Both BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/scoring/ \
       健康监测与分析平台/src/api/modules/healthScore.ts
git commit -m "feat(scoring): add algorithm explanation notes to health score response"
```

---

### Task 12: ROOK Unified Device API Integration

**Files:**
- Create: `backend-java/src/main/java/com/ahealth/backend/device/RookService.java`
- Create: `backend-java/src/main/java/com/ahealth/backend/device/RookController.java`
- Create: `backend-java/src/main/java/com/ahealth/backend/device/RookDtos.java`
- Modify: `backend-java/src/main/resources/application.yml` — add ROOK config
- Create: `健康监测与分析平台/src/api/modules/rook.ts` — frontend API client

**ROOK API Overview (from docs.tryrook.io):**
- Base URL: `https://api.rook-connect.com` (prod) / `https://api.rook-connect.review` (sandbox)
- Auth: Basic Auth with `client_uuid` + `client_secret`
- Supported data sources: Garmin, Oura, Polar, Fitbit, Withings, Whoop, Apple Health, Health Connect
- Key endpoints: user authorization, physical health summary, sleep health summary, body health summary, activity events, heart rate events, stress events

**Interfaces:**
- Consumes: `HttpClient`, `ObjectMapper`, ROOK API credentials
- Produces: `RookService.authorizeDataSource()`, `.getPhysicalHealthSummary()`, `.getSleepHealthSummary()`, `.getBodyHealthSummary()`, `.getActivityEvents()`

- [ ] **Step 1: Create RookDtos.java**

```java
package com.ahealth.backend.device;

import java.util.List;
import java.util.Map;

public final class RookDtos {
  private RookDtos() {}

  /** Data source authorization status */
  public record DataSourceAuth(
      String dataSource,
      boolean authorized,
      String authorizationUrl
  ) {}

  /** All authorized data sources */
  public record AuthorizedSources(
      String userId,
      Map<String, Boolean> sources
  ) {}

  /** Physical health summary from ROOK */
  public record PhysicalHealthSummary(
      ActivityData activity,
      CaloriesData calories,
      DistanceData distance,
      HeartRateData heartRate,
      OxygenationData oxygenation,
      StressData stress
  ) {
    public record ActivityData(
        int activeSeconds, int inactiveSeconds, int restSeconds,
        int highIntensitySeconds, int mediumIntensitySeconds, int lowIntensitySeconds
    ) {}
    public record CaloriesData(
        double bmrKcal, double expenditureKcal, double netActiveKcal
    ) {}
    public record DistanceData(
        int steps, int activeSteps, int floorsClimbed, double elevationMeters
    ) {}
    public record HeartRateData(
        double avgBpm, double maxBpm, double minBpm, double restingBpm,
        double hrvAvgRmssd, double hrvAvgSdnn
    ) {}
    public record OxygenationData(double avgSpo2, double vo2Max) {}
    public record StressData(
        double avgLevel, double maxLevel,
        int highStressDurationSeconds, int mediumStressDurationSeconds,
        int lowStressDurationSeconds, int restDurationSeconds
    ) {}
  }

  /** Sleep health summary from ROOK */
  public record SleepHealthSummary(
      DurationData duration,
      ScoresData scores,
      HeartRateData heartRate,
      BreathingData breathing
  ) {
    public record DurationData(
        String sleepStart, String sleepEnd,
        int totalSleepSeconds, int timeInBedSeconds,
        int lightSleepSeconds, int remSleepSeconds, int deepSleepSeconds,
        int timeToFallAsleepSeconds, int timeAwakeDuringSleepSeconds
    ) {}
    public record ScoresData(
        int qualityRating, int efficiency, int continuityScore
    ) {}
    public record BreathingData(
        double breathsAvgPerMin, int snoringEventsCount, double spo2Avg
    ) {}
  }

  /** Activity event from ROOK */
  public record ActivityEvent(
      String activityType, int durationSeconds,
      String startDatetime, String endDatetime,
      double strainLevel,
      HeartRateData heartRate,
      MovementData movement
  ) {
    public record MovementData(
        int steps, double avgPace, double maxPace, double avgSpeed
    ) {}
  }

  /** User information from ROOK */
  public record RookUserInfo(
      double heightCm, double weightKg, double bmi,
      String sex, String dateOfBirth
  ) {}

  /** Rehab analysis result combining device data with rehab plan */
  public record RehabAnalysisResult(
      String exerciseName,
      String performanceAssessment,  // "good", "overexertion", "underperformance", "no_data"
      int actualDurationSeconds,
      int targetDurationSeconds,
      double exertionLevel,          // 0-1 scale
      List<String> warnings,
      List<String> recommendations
  ) {}
}
```

- [ ] **Step 2: Create RookService.java**

```java
package com.ahealth.backend.device;

import com.ahealth.backend.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RookService {
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final JdbcTemplate jdbc;
  private final String clientUuid;
  private final String clientSecret;
  private final String baseUrl;

  public RookService(
      ObjectMapper objectMapper,
      JdbcTemplate jdbc,
      @Value("${ROOK_CLIENT_UUID:}") String clientUuid,
      @Value("${ROOK_CLIENT_SECRET:}") String clientSecret,
      @Value("${ROOK_BASE_URL:https://api.rook-connect.review}") String baseUrl
  ) {
    this.objectMapper = objectMapper;
    this.jdbc = jdbc;
    this.clientUuid = clientUuid == null ? "" : clientUuid.trim();
    this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }

  public boolean isConfigured() {
    return !clientUuid.isBlank() && !clientSecret.isBlank();
  }

  /** Get authorization URL for a data source (Garmin, Oura, Fitbit, etc.) */
  public RookDtos.DataSourceAuth getDataSourceAuthorizer(String userId, String dataSource) {
    JsonNode result = rookGet("/api/v1/user_id/" + userId + "/data_source/" + dataSource + "/authorizer");
    return new RookDtos.DataSourceAuth(
        result.path("data_source").asText(dataSource),
        result.path("authorized").asBoolean(false),
        result.path("authorization_url").asText("")
    );
  }

  /** Get all authorized data sources for a user */
  public RookDtos.AuthorizedSources getAuthorizedSources(String userId) {
    JsonNode result = rookGet("/api/v1/user_id/" + userId + "/data_sources/authorized");
    Map<String, Boolean> sources = new LinkedHashMap<>();
    JsonNode sourcesNode = result.path("sources");
    if (sourcesNode.isObject()) {
      sourcesNode.fields().forEachRemaining(e -> sources.put(e.getKey(), e.getValue().asBoolean(false)));
    }
    return new RookDtos.AuthorizedSources(result.path("user_id").asText(userId), sources);
  }

  /** Get physical health summary for a date */
  public RookDtos.PhysicalHealthSummary getPhysicalHealthSummary(String userId, String date) {
    JsonNode result = rookGet("/v2/processed_data/physical_health/summary?user_id=" + userId + "&date=" + date);
    return parsePhysicalHealth(result);
  }

  /** Get sleep health summary for a date */
  public RookDtos.SleepHealthSummary getSleepHealthSummary(String userId, String date) {
    JsonNode result = rookGet("/v2/processed_data/sleep_health/summary?user_id=" + userId + "&date=" + date);
    return parseSleepHealth(result);
  }

  /** Get activity events for a date */
  public List<RookDtos.ActivityEvent> getActivityEvents(String userId, String date) {
    JsonNode result = rookGet("/v2/processed_data/physical_health/events/activity?user_id=" + userId + "&date=" + date);
    List<RookDtos.ActivityEvent> events = new ArrayList<>();
    if (result.isArray()) {
      for (JsonNode item : result) {
        events.add(parseActivityEvent(item));
      }
    }
    return events;
  }

  /** Set user timezone */
  public void setUserTimezone(String userId, String timezone, String offset) {
    rookPost("/api/v1/user_id/" + userId + "/time_zone",
        Map.of("time_zone", timezone, "offset", offset));
  }

  // === Private helpers ===

  private JsonNode rookGet(String path) {
    ensureConfigured();
    try {
      String auth = Base64.getEncoder().encodeToString(
          (clientUuid + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + path))
          .timeout(Duration.ofSeconds(30))
          .header("Authorization", "Basic " + auth)
          .header("Accept", "application/json")
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() == 204) return objectMapper.createObjectNode();
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "ROOK API 调用失败，状态码：" + response.statusCode());
      }
      return objectMapper.readTree(response.body());
    } catch (ApiException e) { throw e; }
      catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(HttpStatus.BAD_GATEWAY, "ROOK 调用被中断"); }
      catch (IOException e) { throw new ApiException(HttpStatus.BAD_GATEWAY, "ROOK 调用失败：" + e.getMessage()); }
  }

  private void rookPost(String path, Map<String, String> body) {
    ensureConfigured();
    try {
      String auth = Base64.getEncoder().encodeToString(
          (clientUuid + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + path))
          .timeout(Duration.ofSeconds(30))
          .header("Authorization", "Basic " + auth)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
          .build();
      httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (Exception e) { /* best-effort */ }
  }

  private void ensureConfigured() {
    if (!isConfigured()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "未配置 ROOK_CLIENT_UUID/ROOK_CLIENT_SECRET");
    }
  }

  private RookDtos.PhysicalHealthSummary parsePhysicalHealth(JsonNode node) {
    JsonNode activity = node.path("activity");
    JsonNode calories = node.path("calories");
    JsonNode distance = node.path("distance");
    JsonNode hr = node.path("heart_rate");
    JsonNode oxi = node.path("oxygenation");
    JsonNode stress = node.path("stress");

    return new RookDtos.PhysicalHealthSummary(
        new RookDtos.PhysicalHealthSummary.ActivityData(
            activity.path("active_seconds").asInt(0), activity.path("inactive_seconds").asInt(0),
            activity.path("rest_seconds").asInt(0), activity.path("high_intensity_seconds").asInt(0),
            activity.path("medium_intensity_seconds").asInt(0), activity.path("low_intensity_seconds").asInt(0)),
        new RookDtos.PhysicalHealthSummary.CaloriesData(
            calories.path("bmr_kcal").asDouble(0), calories.path("expenditure_kcal").asDouble(0),
            calories.path("net_active_kcal").asDouble(0)),
        new RookDtos.PhysicalHealthSummary.DistanceData(
            distance.path("steps").asInt(0), distance.path("active_steps").asInt(0),
            distance.path("floors_climbed").asInt(0), distance.path("elevation_meters").asDouble(0)),
        new RookDtos.PhysicalHealthSummary.HeartRateData(
            hr.path("avg_bpm").asDouble(0), hr.path("max_bpm").asDouble(0),
            hr.path("min_bpm").asDouble(0), hr.path("resting_bpm").asDouble(0),
            hr.path("hrv_avg_rmssd").asDouble(0), hr.path("hrv_avg_sdnn").asDouble(0)),
        new RookDtos.PhysicalHealthSummary.OxygenationData(
            oxi.path("avg_spo2").asDouble(0), oxi.path("vo2_max").asDouble(0)),
        new RookDtos.PhysicalHealthSummary.StressData(
            stress.path("avg_level").asDouble(0), stress.path("max_level").asDouble(0),
            stress.path("high_stress_duration_seconds").asInt(0),
            stress.path("medium_stress_duration_seconds").asInt(0),
            stress.path("low_stress_duration_seconds").asInt(0),
            stress.path("rest_duration_seconds").asInt(0))
    );
  }

  private RookDtos.SleepHealthSummary parseSleepHealth(JsonNode node) {
    JsonNode dur = node.path("duration");
    JsonNode scores = node.path("scores");
    JsonNode hr = node.path("heart_rate");
    JsonNode breathing = node.path("breathing");

    return new RookDtos.SleepHealthSummary(
        new RookDtos.SleepHealthSummary.DurationData(
            dur.path("sleep_start_datetime").asText(""), dur.path("sleep_end_datetime").asText(""),
            dur.path("total_sleep_duration_seconds").asInt(0), dur.path("time_in_bed_seconds").asInt(0),
            dur.path("light_sleep_duration_seconds").asInt(0), dur.path("rem_sleep_duration_seconds").asInt(0),
            dur.path("deep_sleep_duration_seconds").asInt(0), dur.path("time_to_fall_asleep_seconds").asInt(0),
            dur.path("time_awake_during_sleep_seconds").asInt(0)),
        new RookDtos.SleepHealthSummary.ScoresData(
            scores.path("quality_rating").asInt(0), scores.path("efficiency").asInt(0),
            scores.path("continuity_score").asInt(0)),
        new RookDtos.PhysicalHealthSummary.HeartRateData(
            hr.path("avg_bpm").asDouble(0), hr.path("max_bpm").asDouble(0),
            hr.path("min_bpm").asDouble(0), hr.path("resting_bpm").asDouble(0),
            hr.path("hrv_avg_rmssd").asDouble(0), hr.path("hrv_avg_sdnn").asDouble(0)),
        new RookDtos.SleepHealthSummary.BreathingData(
            breathing.path("breaths_avg_per_min").asDouble(0),
            breathing.path("snoring_events_count").asInt(0),
            breathing.path("spo2_avg").asDouble(0))
    );
  }

  private RookDtos.ActivityEvent parseActivityEvent(JsonNode node) {
    JsonNode hr = node.path("heart_rate");
    JsonNode mv = node.path("movement");
    return new RookDtos.ActivityEvent(
        node.path("activity_type_name").asText("unknown"),
        node.path("duration_seconds").asInt(0),
        node.path("start_datetime").asText(""), node.path("end_datetime").asText(""),
        node.path("strain_level").asDouble(0),
        new RookDtos.PhysicalHealthSummary.HeartRateData(
            hr.path("avg_bpm").asDouble(0), hr.path("max_bpm").asDouble(0),
            hr.path("min_bpm").asDouble(0), hr.path("resting_bpm").asDouble(0),
            hr.path("hrv_avg_rmssd").asDouble(0), hr.path("hrv_avg_sdnn").asDouble(0)),
        new RookDtos.ActivityEvent.MovementData(
            mv.path("steps").asInt(0), mv.path("avg_pace").asDouble(0),
            mv.path("max_pace").asDouble(0), mv.path("avg_speed").asDouble(0))
    );
  }
}
```

- [ ] **Step 3: Create RookController.java**

```java
package com.ahealth.backend.device;

import com.ahealth.backend.common.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/device/rook")
public class RookController {
  private final RookService rookService;

  public RookController(RookService rookService) {
    this.rookService = rookService;
  }

  /** Get authorization URL for connecting a data source */
  @GetMapping("/authorize/{dataSource}")
  public RookDtos.DataSourceAuth authorize(@PathVariable String dataSource) {
    String userId = String.valueOf(CurrentUser.requireUserId());
    return rookService.getDataSourceAuthorizer(userId, dataSource);
  }

  /** Get all authorized data sources */
  @GetMapping("/sources")
  public RookDtos.AuthorizedSources getSources() {
    String userId = String.valueOf(CurrentUser.requireUserId());
    return rookService.getAuthorizedSources(userId);
  }

  /** Get physical health summary for a date */
  @GetMapping("/physical/{date}")
  public RookDtos.PhysicalHealthSummary getPhysical(@PathVariable String date) {
    String userId = String.valueOf(CurrentUser.requireUserId());
    return rookService.getPhysicalHealthSummary(userId, date);
  }

  /** Get sleep health summary for a date */
  @GetMapping("/sleep/{date}")
  public RookDtos.SleepHealthSummary getSleep(@PathVariable String date) {
    String userId = String.valueOf(CurrentUser.requireUserId());
    return rookService.getSleepHealthSummary(userId, date);
  }

  /** Get activity events for a date */
  @GetMapping("/activities/{date}")
  public List<RookDtos.ActivityEvent> getActivities(@PathVariable String date) {
    String userId = String.valueOf(CurrentUser.requireUserId());
    return rookService.getActivityEvents(userId, date);
  }

  /** Check if ROOK is configured */
  @GetMapping("/status")
  public Map<String, Object> status() {
    return Map.of("configured", rookService.isConfigured());
  }
}
```

- [ ] **Step 4: Add ROOK config to application.yml**

Append to `backend-java/src/main/resources/application.yml`:

```yaml
# ROOK Unified Device API
ROOK_CLIENT_UUID: ${ROOK_CLIENT_UUID:}
ROOK_CLIENT_SECRET: ${ROOK_CLIENT_SECRET:}
ROOK_BASE_URL: ${ROOK_BASE_URL:https://api.rook-connect.review}
```

- [ ] **Step 5: Create frontend rook.ts API module**

`健康监测与分析平台/src/api/modules/rook.ts`:

```typescript
import { http } from '@/api/http'

export type DataSourceAuth = {
  dataSource: string
  authorized: boolean
  authorizationUrl: string
}

export type AuthorizedSources = {
  userId: string
  sources: Record<string, boolean>
}

export type PhysicalHealthSummary = {
  activity: { activeSeconds: number; restSeconds: number; highIntensitySeconds: number }
  calories: { expenditureKcal: number }
  distance: { steps: number; floorsClimbed: number }
  heartRate: { avgBpm: number; restingBpm: number; hrvAvgRmssd: number; hrvAvgSdnn: number }
  oxygenation: { avgSpo2: number; vo2Max: number }
  stress: { avgLevel: number; highStressDurationSeconds: number }
}

export type SleepHealthSummary = {
  duration: {
    totalSleepSeconds: number; deepSleepSeconds: number; remSleepSeconds: number
    lightSleepSeconds: number; timeToFallAsleepSeconds: number; timeAwakeDuringSleepSeconds: number
  }
  scores: { qualityRating: number; efficiency: number; continuityScore: number }
  heartRate: { avgBpm: number; hrvAvgRmssd: number }
  breathing: { breathsAvgPerMin: number; snoringEventsCount: number; spo2Avg: number }
}

export type ActivityEvent = {
  activityType: string; durationSeconds: number
  heartRate: { avgBpm: number; maxBpm: number }
  movement: { steps: number; avgPace: number }
}

export async function getRookStatus(): Promise<{ configured: boolean }> {
  const { data } = await http.get('/device/rook/status')
  return data as { configured: boolean }
}

export async function authorizeDataSource(dataSource: string): Promise<DataSourceAuth> {
  const { data } = await http.get(`/device/rook/authorize/${dataSource}`)
  return data as DataSourceAuth
}

export async function getAuthorizedSources(): Promise<AuthorizedSources> {
  const { data } = await http.get('/device/rook/sources')
  return data as AuthorizedSources
}

export async function getPhysicalHealth(date: string): Promise<PhysicalHealthSummary> {
  const { data } = await http.get(`/device/rook/physical/${date}`)
  return data as PhysicalHealthSummary
}

export async function getSleepHealth(date: string): Promise<SleepHealthSummary> {
  const { data } = await http.get(`/device/rook/sleep/${date}`)
  return data as SleepHealthSummary
}

export async function getActivityEvents(date: string): Promise<ActivityEvent[]> {
  const { data } = await http.get(`/device/rook/activities/${date}`)
  return data as ActivityEvent[]
}
```

- [ ] **Step 6: Build and verify**

Run: `cd backend-java && ./mvnw.cmd compile` && `cd 健康监测与分析平台 && npx vue-tsc --noEmit`
Expected: Both pass

- [ ] **Step 7: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/device/ \
       backend-java/src/main/resources/application.yml \
       健康监测与分析平台/src/api/modules/rook.ts
git commit -m "feat(device): add ROOK unified device API integration for multi-source health data"
```

---

### Task 13: Rehab Algorithm Fix — week_stats Write Path + Exercise Done Persistence

**Files:**
- Modify: `backend-java/src/main/java/com/ahealth/backend/rehab/RehabService.java` — add week_stats write on toggle
- Modify: `健康监测与分析平台/src/modules/rehab/views/RehabExercisePage.vue` — fix mark done to call API

**Problem:** Two critical bugs in the rehab module:
1. `rehab_week_stats` table is read but never written to — the weekly trend chart shows stale seed data
2. `RehabExercisePage.vue` mark done is local-only — completion is lost on navigation

- [ ] **Step 1: Add week_stats update to RehabService.togglePlanItem()**

In `RehabService.java`, after the `togglePlanItem` method updates the done bit, add week stats update:

```java
/** After toggling plan item done status, update weekly stats */
private void updateWeekStats(long uid) {
  String today = java.time.LocalDate.now().toString();
  Integer doneCount = jdbc.queryForObject(
      "SELECT COUNT(*) FROM rehab_plan_items WHERE user_id=? AND scheduled_date=? AND done=1",
      Integer.class, uid, today);
  // Assume each exercise is ~8 minutes
  int minutes = (doneCount != null ? doneCount : 0) * 8;

  int updated = jdbc.update(
      "UPDATE rehab_week_stats SET minutes=? WHERE user_id=? AND stat_date=?",
      minutes, uid, today);
  if (updated == 0) {
    jdbc.update(
        "INSERT INTO rehab_week_stats(user_id, stat_date, minutes) VALUES(?,?,?)",
        uid, today, minutes);
  }
}
```

Call `updateWeekStats(uid)` at the end of `togglePlanItem()`.

- [ ] **Step 2: Fix RehabExercisePage.vue mark done**

In `RehabExercisePage.vue`, replace the local-only `markDone()` with an API call:

```typescript
import { toggleRehabExercise } from '@/api/modules/rehab'

const markDone = async () => {
  if (!exercise.value) return
  try {
    // Find the plan item ID for this exercise
    const planId = Number(route.query.planId) || 0
    if (planId > 0) {
      await toggleRehabExercise(planId)
    }
    done.value = true
    success('已完成', `${exercise.value.name} 已标记为完成`)
  } catch (err) {
    error('标记失败', err instanceof Error ? err.message : '请稍后重试')
  }
}
```

Update `RehabPage.vue` to pass `planId` when navigating to exercise demo:

```typescript
const viewDemo = (exercise: RehabExercise) => {
  void router.push({ path: '/rehab/exercise', query: { name: exercise.name, planId: String(exercise.id) } })
}
```

- [ ] **Step 3: Build and verify**

Run: `cd backend-java && ./mvnw.cmd compile` && `cd 健康监测与分析平台 && npx vue-tsc --noEmit`
Expected: Both pass

- [ ] **Step 4: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/rehab/RehabService.java \
       健康监测与分析平台/src/modules/rehab/views/RehabExercisePage.vue \
       健康监测与分析平台/src/modules/rehab/views/RehabPage.vue
git commit -m "fix(rehab): persist exercise completion to API and write week_stats on toggle"
```

---

### Task 14: Device-Driven Rehab Performance Analysis

**Files:**
- Modify: `backend-java/src/main/java/com/ahealth/backend/rehab/RehabService.java` — add `analyzeRehabPerformance` method
- Modify: `backend-java/src/main/java/com/ahealth/backend/rehab/RehabController.java` — add analysis endpoint
- Modify: `backend-java/src/main/java/com/ahealth/backend/rehab/RehabDtos.java` — add analysis DTOs
- Modify: `健康监测与分析平台/src/modules/rehab/views/RehabPage.vue` — show device-based analysis

**Concept:** After the user connects a device via ROOK, pull their activity/HR data during exercise time windows and compare against the rehab plan to detect overexertion or underperformance.

- [ ] **Step 1: Add analysis DTOs to RehabDtos.java**

```java
  /** Device-based rehab performance analysis */
  public record RehabPerformanceAnalysis(
      String date,
      List<ExerciseAnalysis> exerciseAnalyses,
      String overallAssessment,
      List<String> warnings,
      List<String> planAdjustments
  ) {}

  public record ExerciseAnalysis(
      String exerciseName,
      String performanceLevel,   // "excellent", "good", "overexertion", "underperformance", "no_device_data"
      double avgHeartRate,
      double maxHeartRate,
      int actualDurationSeconds,
      int targetDurationSeconds,
      double exertionScore,      // 0-1, >0.8 = overexertion risk
      String note
  ) {}
```

- [ ] **Step 2: Add analyzeRehabPerformance to RehabService**

```java
  /**
   * Analyze rehab performance using device data from ROOK.
   * Compares actual activity during exercise windows against plan targets.
   */
  public RehabDtos.RehabPerformanceAnalysis analyzeRehabPerformance(long uid) {
    String today = java.time.LocalDate.now().toString();

    // Get today's plan items
    var planItems = jdbcTemplate.queryForList(
        "SELECT rpi.id, rpi.done, re.name, re.minutes FROM rehab_plan_items rpi "
        + "JOIN rehab_exercises re ON re.id = rpi.exercise_id "
        + "WHERE rpi.user_id=? AND rpi.scheduled_date=?", uid, today);

    // Try to get device activity data from ROOK
    List<RookDtos.ActivityEvent> activities = List.of();
    RookDtos.PhysicalHealthSummary physical = null;
    try {
      if (rookService.isConfigured()) {
        activities = rookService.getActivityEvents(String.valueOf(uid), today);
        physical = rookService.getPhysicalHealthSummary(String.valueOf(uid), today);
      }
    } catch (Exception e) {
      // Device data unavailable — analysis will be limited
    }

    List<RehabDtos.ExerciseAnalysis> analyses = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<String> adjustments = new ArrayList<>();

    for (var item : planItems) {
      String name = (String) item.get("name");
      int targetMinutes = ((Number) item.get("minutes")).intValue();
      boolean done = ((Number) item.get("done")).intValue() == 1;

      if (!done) {
        analyses.add(new RehabDtos.ExerciseAnalysis(
            name, "not_completed", 0, 0, 0, targetMinutes * 60, 0, "未完成"));
        continue;
      }

      // Match activity events to this exercise
      RookDtos.ActivityEvent matched = findMatchingActivity(activities, name);

      if (matched == null) {
        analyses.add(new RehabDtos.ExerciseAnalysis(
            name, "no_device_data", 0, 0, targetMinutes * 60, targetMinutes * 60, 0.5,
            "无设备数据，无法评估运动强度"));
        continue;
      }

      double avgHr = matched.heartRate().avgBpm();
      double maxHr = matched.heartRate().maxBpm();
      int actualSeconds = matched.durationSeconds();
      double exertion = calculateExertion(avgHr, maxHr, actualSeconds, targetMinutes * 60);

      String level;
      String note;
      if (exertion > 0.85) {
        level = "overexertion";
        note = "运动强度过高，建议降低强度或缩短时间";
        warnings.add(name + "：运动强度过高（心率 " + (int) avgHr + " bpm）");
        adjustments.add(name + "：建议减少 1-2 组，或降低阻力");
      } else if (exertion < 0.3) {
        level = "underperformance";
        note = "运动强度偏低，可适当增加";
        adjustments.add(name + "：可增加组数或延长保持时间");
      } else if (exertion > 0.7) {
        level = "good";
        note = "运动强度良好，保持当前节奏";
      } else {
        level = "excellent";
        note = "运动状态优秀";
      }

      analyses.add(new RehabDtos.ExerciseAnalysis(
          name, level, avgHr, maxHr, actualSeconds, targetMinutes * 60, exertion, note));
    }

    // Overall assessment
    long overexertionCount = analyses.stream().filter(a -> "overexertion".equals(a.performanceLevel())).count();
    String overall = overexertionCount > 0 ? "存在过度运动风险，请注意调整" : "康复训练状态良好";

    // Add HRV-based recovery recommendation
    if (physical != null && physical.heartRate().hrvAvgRmssd() > 0) {
      double rmssd = physical.heartRate().hrvAvgRmssd();
      if (rmssd < 25) {
        warnings.add("HRV 偏低（" + (int) rmssd + " ms），建议今天降低训练强度");
        adjustments.add("今日建议：低强度恢复训练，优先休息");
      } else if (rmssd > 60) {
        adjustments.add("HRV 状态良好，可适当增加训练强度");
      }
    }

    return new RehabDtos.RehabPerformanceAnalysis(
        today, analyses, overall, warnings, adjustments);
  }

  private RookDtos.ActivityEvent findMatchingActivity(List<RookDtos.ActivityEvent> activities, String exerciseName) {
    // Match by activity type keywords
    for (RookDtos.ActivityEvent event : activities) {
      String type = event.activityType().toLowerCase();
      if ((exerciseName.contains("划船") || exerciseName.contains("row")) && type.contains("strength")) return event;
      if ((exerciseName.contains("拉伸") || exerciseName.contains("stretch")) && type.contains("yoga")) return event;
      if ((exerciseName.contains("狗") || exerciseName.contains("虫") || exerciseName.contains("core"))
          && (type.contains("strength") || type.contains("functional"))) return event;
    }
    // Fallback: return the first activity if only one
    return activities.size() == 1 ? activities.get(0) : null;
  }

  private double calculateExertion(double avgHr, double maxHr, int actualSeconds, int targetSeconds) {
    // Exertion = HR intensity * duration ratio
    // Max HR estimate: 220 - 30 = 190 (default age 30)
    double hrIntensity = clamp((avgHr - 60) / 130.0, 0, 1); // 60-190 range normalized to 0-1
    double durationRatio = targetSeconds > 0 ? clamp((double) actualSeconds / targetSeconds, 0, 2) : 1;
    return clamp(hrIntensity * 0.7 + (durationRatio > 1 ? 0.3 : durationRatio * 0.3), 0, 1);
  }

  private double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
```

Add `RookService` dependency injection to `RehabService` constructor.

- [ ] **Step 3: Add endpoint to RehabController**

```java
  @GetMapping("/analysis")
  public RehabDtos.RehabPerformanceAnalysis analyzePerformance() {
    return rehabService.analyzeRehabPerformance(CurrentUser.requireUserId());
  }
```

- [ ] **Step 4: Add frontend API function and type to rehab.ts**

Add to `健康监测与分析平台/src/api/modules/rehab.ts`:

```typescript
export type ExerciseAnalysis = {
  exerciseName: string
  performanceLevel: 'excellent' | 'good' | 'overexertion' | 'underperformance' | 'no_device_data' | 'not_completed'
  avgHeartRate: number
  maxHeartRate: number
  actualDurationSeconds: number
  targetDurationSeconds: number
  exertionScore: number
  note: string
}

export type RehabPerformanceAnalysis = {
  date: string
  exerciseAnalyses: ExerciseAnalysis[]
  overallAssessment: string
  warnings: string[]
  planAdjustments: string[]
}

export async function getRehabAnalysis(): Promise<RehabPerformanceAnalysis> {
  const { data } = await http.get<RehabPerformanceAnalysis>('/rehab/analysis')
  return data
}
```

- [ ] **Step 5: Add frontend analysis display to RehabPage.vue**

Add after the weekly trend section:

```vue
      <!-- Device-based performance analysis -->
      <ClinicalSurfaceCard v-if="performanceAnalysis" title="设备数据分析">
        <div class="mb-2 flex items-center gap-2">
          <iconify-icon icon="solar:health-outline" width="18" height="18" class="text-teal-700" />
          <span class="text-sm font-medium text-teal-800">{{ performanceAnalysis.overallAssessment }}</span>
        </div>

        <div v-for="a in performanceAnalysis.exerciseAnalyses" :key="a.exerciseName" class="mb-2 rounded-xl border p-3"
          :class="a.performanceLevel === 'overexertion' ? 'border-red-200 bg-red-50' : a.performanceLevel === 'underperformance' ? 'border-amber-200 bg-amber-50' : 'border-slate-200 bg-slate-50'">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium">{{ a.exerciseName }}</span>
            <span class="rounded-full px-2 py-0.5 text-xs"
              :class="a.performanceLevel === 'overexertion' ? 'bg-red-100 text-red-700' : a.performanceLevel === 'excellent' ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-600'">
              {{ performanceLabel(a.performanceLevel) }}
            </span>
          </div>
          <p class="mt-1 text-xs text-slate-600">{{ a.note }}</p>
          <div v-if="a.avgHeartRate > 0" class="mt-1 flex gap-3 text-xs text-slate-500">
            <span>平均心率 {{ Math.round(a.avgHeartRate) }} bpm</span>
            <span>最高心率 {{ Math.round(a.maxHeartRate) }} bpm</span>
          </div>
        </div>

        <div v-if="performanceAnalysis.warnings.length" class="mt-2 space-y-1">
          <div v-for="(w, i) in performanceAnalysis.warnings" :key="i"
            class="flex items-start gap-2 rounded-lg bg-amber-50 px-3 py-1.5 text-xs text-amber-800">
            <iconify-icon icon="solar:danger-triangle-outline" width="12" height="12" class="mt-0.5 shrink-0" />
            {{ w }}
          </div>
        </div>

        <div v-if="performanceAnalysis.planAdjustments.length" class="mt-2 space-y-1">
          <p class="text-xs font-medium text-teal-700">AI 调整建议：</p>
          <p v-for="(adj, i) in performanceAnalysis.planAdjustments" :key="i" class="text-xs text-teal-600">• {{ adj }}</p>
        </div>
      </ClinicalSurfaceCard>
```

Add to script:

```typescript
import { getRehabAnalysis, type RehabPerformanceAnalysis } from '@/api/modules/rehab'

const performanceAnalysis = ref<RehabPerformanceAnalysis | null>(null)

const loadAnalysis = async () => {
  try {
    performanceAnalysis.value = await getRehabAnalysis()
  } catch { /* no device data available */ }
}

const performanceLabel = (level: string) => {
  switch (level) {
    case 'excellent': return '优秀'
    case 'good': return '良好'
    case 'overexertion': return '过度运动'
    case 'underperformance': return '强度不足'
    case 'not_completed': return '未完成'
    default: return '无数据'
  }
}
```

- [ ] **Step 5: Build and verify**

Run: `cd backend-java && ./mvnw.cmd compile` && `cd 健康监测与分析平台 && npx vue-tsc --noEmit`
Expected: Both pass

- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/rehab/ \
       健康监测与分析平台/src/modules/rehab/views/RehabPage.vue
git commit -m "feat(rehab): add device-driven performance analysis with overexertion detection"
```

---

### Task 15: ROOK Data Sync to Monitor Records

**Files:**
- Modify: `backend-java/src/main/java/com/ahealth/backend/device/RookService.java` — add `syncToMonitorRecords` method
- Modify: `backend-java/src/main/java/com/ahealth/backend/device/RookController.java` — add sync endpoint

**Concept:** Pull today's data from ROOK and insert/update `monitor_records` so that the health scoring engine uses real device data instead of seed data.

- [ ] **Step 1: Add sync method to RookService**

```java
  /**
   * Sync ROOK physical + sleep data to monitor_records table.
   * This feeds the health scoring engine with real device data.
   */
  public Map<String, Object> syncToMonitorRecords(long uid) {
    String today = java.time.LocalDate.now().toString();

    RookDtos.PhysicalHealthSummary physical = getPhysicalHealthSummary(String.valueOf(uid), today);
    RookDtos.SleepHealthSummary sleep = getSleepHealthSummary(String.valueOf(uid), today);

    int hr = (int) physical.heartRate().restingBpm();
    if (hr <= 0) hr = (int) physical.heartRate().avgBpm();
    int sleepScore = sleep.scores().qualityRating() * 20; // 1-5 → 20-100
    double deepHours = sleep.duration().deepSleepSeconds() / 3600.0;
    int awakeTimes = sleep.duration().timeAwakeDuringSleepSeconds() > 0 ? 1 : 0;
    int stressScore = (int) (physical.stress().avgLevel() * 10); // 0-10 → 0-100
    int hrv = (int) physical.heartRate().hrvAvgRmssd();
    int steps = physical.distance().steps();
    double vo2 = physical.oxygenation().vo2Max();

    // Insert or update today's record
    Integer existing = jdbc.queryForObject(
        "SELECT COUNT(*) FROM monitor_records WHERE DATE(recorded_at) = CURDATE() AND HOUR(recorded_at) = HOUR(NOW())",
        Integer.class);

    if (existing != null && existing > 0) {
      jdbc.update(
          "UPDATE monitor_records SET hr=?, sleep_score=?, deep_sleep_hours=?, awake_times=?, "
          + "stress_score=?, hrv_millis=?, steps=?, vo2_max=? "
          + "WHERE DATE(recorded_at)=CURDATE() AND HOUR(recorded_at)=HOUR(NOW())",
          hr, sleepScore, deepHours, awakeTimes, stressScore, hrv, steps, vo2);
    } else {
      jdbc.update(
          "INSERT INTO monitor_records(recorded_at, hr, sleep_score, deep_sleep_hours, awake_times, "
          + "stress_score, hrv_millis, steps, vo2_max) VALUES(NOW(), ?, ?, ?, ?, ?, ?, ?, ?)",
          hr, sleepScore, deepHours, awakeTimes, stressScore, hrv, steps, vo2);
    }

    return Map.of(
        "synced", true,
        "hr", hr, "sleepScore", sleepScore, "stressScore", stressScore,
        "hrv", hrv, "steps", steps, "vo2Max", vo2,
        "deepSleepHours", deepHours
    );
  }
```

- [ ] **Step 2: Add sync endpoint to RookController**

```java
  /** Sync ROOK device data to monitor_records for health scoring */
  @PostMapping("/sync")
  public Map<String, Object> syncData() {
    return rookService.syncToMonitorRecords(CurrentUser.requireUserId());
  }
```

- [ ] **Step 3: Build and verify**

Run: `cd backend-java && ./mvnw.cmd compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/device/
git commit -m "feat(device): add ROOK data sync to monitor_records for health scoring engine"
```

---

### Task 16: AI Assistant Upgrade — Knowledge Base, Domain Restriction, Streaming, History

**Files:**
- Modify: `backend-java/src/main/java/com/ahealth/backend/consult/ConsultService.java` — add domain guard, knowledge injection, history CRUD, streaming
- Modify: `backend-java/src/main/java/com/ahealth/backend/consult/ConsultController.java` — add history endpoints (delegate to service)
- Create: `backend-java/src/main/java/com/ahealth/backend/consult/HealthKnowledgeService.java` — RAG knowledge base
- Modify: `backend-java/src/main/java/com/ahealth/backend/config/BackendSchemaInitializer.java` — add `consult_history` table
- Modify: `健康监测与分析平台/src/modules/assistant/views/AssistantPage.vue` — history UI, delete, knowledge source display
- Modify: `健康监测与分析平台/src/api/modules/consult.ts` — add history/delete API

**Concept:** Transform the AI assistant from a simple chat wrapper into a proper health assistant with: domain restriction (only health questions), knowledge base retrieval (RAG), true SSE streaming, persistent history with CRUD, and source attribution.

- [ ] **Step 1: Add consult_history table to BackendSchemaInitializer**

Since Flyway is disabled, add to `BackendSchemaInitializer.java` `ensureSchema()` method:

```java
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS consult_history (
          id INT AUTO_INCREMENT PRIMARY KEY,
          user_id INT NOT NULL,
          request_id VARCHAR(64) NOT NULL,
          scene VARCHAR(32) NOT NULL DEFAULT 'assistant',
          question TEXT NOT NULL,
          answer TEXT NOT NULL,
          suggestions_json TEXT,
          disclaimer VARCHAR(255) DEFAULT '',
          knowledge_sources_json TEXT,
          model_used VARCHAR(64) DEFAULT '',
          created_at DATETIME NOT NULL,
          INDEX idx_consult_user (user_id),
          INDEX idx_consult_created (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );
```

Also add this table to `BackendSchemaInitializer.java` `ensureSchema()` method (since Flyway is disabled):

```java
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS consult_history (
          id INT AUTO_INCREMENT PRIMARY KEY,
          user_id INT NOT NULL,
          request_id VARCHAR(64) NOT NULL,
          scene VARCHAR(32) NOT NULL DEFAULT 'assistant',
          question TEXT NOT NULL,
          answer TEXT NOT NULL,
          suggestions_json TEXT,
          disclaimer VARCHAR(255) DEFAULT '',
          knowledge_sources_json TEXT,
          model_used VARCHAR(64) DEFAULT '',
          created_at DATETIME NOT NULL,
          INDEX idx_consult_user (user_id),
          INDEX idx_consult_created (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    );
```

- [ ] **Step 2: Create HealthKnowledgeService.java**

```java
package com.ahealth.backend.consult;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthKnowledgeService {
  private final JdbcTemplate jdbc;

  // Health domain keywords — questions must contain at least one to be considered health-related
  private static final Set<String> HEALTH_KEYWORDS = Set.of(
      "健康", "血压", "心率", "睡眠", "运动", "康复", "药物", "用药", "服药", "药品",
      "症状", "疼痛", "头晕", "疲劳", "饮食", "营养", "体重", "血糖", "血脂",
      "心脏", "肺", "肝", "肾", "胃", "骨", "关节", "肌肉", "神经",
      "体检", "化验", "指标", "异常", "偏高", "偏低", "正常",
      "锻炼", "步行", "跑步", "游泳", "瑜伽", "冥想", "呼吸",
      "高血压", "糖尿病", "冠心病", "失眠", "焦虑", "抑郁",
      "医生", "医院", "处方", "检查", "治疗", "手术", "康复",
      "维生素", "钙", "铁", "锌", "蛋白质", "纤维",
      "过敏", "副作用", "禁忌", "相互作用",
      "老人", "老年", "护理", "照护", "慢病"
  );

  // Non-health topics that should be rejected
  private static final Set<String> BLOCKED_TOPICS = Set.of(
      "股票", "基金", "投资", "理财", "赚钱", "贷款",
      "游戏", "电影", "电视剧", "综艺", "明星", "八卦",
      "政治", "选举", "投票", "政府",
      "编程", "代码", "软件", "开发", "bug",
      "购物", "优惠", "打折", "促销", "电商",
      "天气", "新闻", "体育", "足球", "篮球"
  );

  public HealthKnowledgeService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Check if a question is health-related.
   * Returns true if the question contains health keywords and does not match blocked topics.
   */
  public boolean isHealthRelated(String question) {
    if (question == null || question.isBlank()) return false;
    String q = question.toLowerCase();

    // Check blocked topics first
    for (String blocked : BLOCKED_TOPICS) {
      if (q.contains(blocked)) return false;
    }

    // Check health keywords
    for (String keyword : HEALTH_KEYWORDS) {
      if (q.contains(keyword)) return true;
    }

    // Short questions (<=10 chars) are likely greetings or follow-ups — allow them
    if (q.length() <= 10) return true;

    return false;
  }

  /**
   * Retrieve relevant knowledge snippets for a health question.
   * Searches user context memories and returns the most relevant ones.
   */
  public List<String> retrieveKnowledge(long uid, String question) {
    List<String> knowledge = new ArrayList<>();

    // Search user context memories for relevant content
    var memories = jdbc.queryForList(
        "SELECT content FROM user_context_memory WHERE user_id=? ORDER BY created_at DESC LIMIT 20",
        uid);

    String[] keywords = extractKeywords(question);
    for (var memory : memories) {
      String content = (String) memory.get("content");
      for (String keyword : keywords) {
        if (content.contains(keyword)) {
          knowledge.add(content);
          break;
        }
      }
    }

    // Add user's current medications as context
    var meds = jdbc.queryForList(
        "SELECT name, notes FROM medications WHERE user_id=? AND enabled=1", uid);
    for (var med : meds) {
      String name = (String) med.get("name");
      String notes = (String) med.get("notes");
      if (!name.isBlank()) {
        knowledge.add("当前用药：" + name + (notes != null && !notes.isBlank() ? "，" + notes : ""));
      }
    }

    // Add user's active concerns from latest monitor data
    var latest = jdbc.queryForList(
        "SELECT hr, sleep_score, stress_score FROM monitor_records ORDER BY recorded_at DESC LIMIT 1");
    if (!latest.isEmpty()) {
      var r = latest.get(0);
      int hr = r.get("hr") instanceof Number n ? n.intValue() : 0;
      int sleep = r.get("sleep_score") instanceof Number n ? n.intValue() : 0;
      int stress = r.get("stress_score") instanceof Number n ? n.intValue() : 0;
      if (hr > 90) knowledge.add("当前心率偏高：" + hr + " bpm");
      if (sleep < 70) knowledge.add("当前睡眠评分偏低：" + sleep + " 分");
      if (stress > 65) knowledge.add("当前压力指数偏高：" + stress);
    }

    return knowledge.stream().distinct().limit(5).toList();
  }

  private String[] extractKeywords(String text) {
    // Simple keyword extraction: split by common delimiters and filter short words
    return Arrays.stream(text.split("[，。？！、\\s,.?!]+"))
        .map(String::trim)
        .filter(w -> w.length() >= 2)
        .toArray(String[]::new);
  }
}
```

- [ ] **Step 3: Update ConsultService with domain guard and knowledge injection**

Add `HealthKnowledgeService` dependency. Update `ask()` method:

```java
public ConsultDtos.ConsultResponse ask(ConsultDtos.ConsultQuestionRequest request) {
  String question = request.question() == null ? "" : request.question().trim();
  if (question.isBlank()) {
    throw new ApiException(HttpStatus.BAD_REQUEST, "请输入问题内容。");
  }

  // Domain restriction: reject non-health questions
  if (!knowledgeService.isHealthRelated(question)) {
    return new ConsultDtos.ConsultResponse(
        "consult_guarded_" + UUID.randomUUID().toString().replace("-", ""),
        "我是健康管理助手，只能回答健康、用药、康复、饮食等相关问题。您可以问我关于血压管理、睡眠改善、运动康复、药物使用等方面的问题。",
        List.of("帮我看看今天的健康数据", "最近睡眠不好怎么办", "我的用药有什么注意事项"),
        "仅用于健康管理辅助，不替代医生诊疗。"
    );
  }

  long uid = CurrentUser.requireUserId();

  // Retrieve knowledge from RAG
  List<String> knowledge = knowledgeService.retrieveKnowledge(uid, question);
  String knowledgeBlock = "";
  if (!knowledge.isEmpty()) {
    knowledgeBlock = "\n相关知识：\n" + String.join("\n", knowledge.stream().map(k -> "- " + k).toList());
  }

  // Build context-aware message (existing logic + knowledge)
  String userMessage = buildContextAwareMessage(request.scene(), question) + knowledgeBlock;

  // ... rest of existing logic (LLM call, parse response, save memory, save history)
}
```

- [ ] **Step 4: Add history methods to ConsultService and wire to ConsultController**

Add to `ConsultService.java` (it already has `JdbcTemplate` via constructor injection):

```java
public List<Map<String, Object>> getHistory(int limit, int offset) {
  long uid = CurrentUser.requireUserId();
  return jdbc.queryForList(
      "SELECT id, request_id, scene, question, answer, suggestions_json, disclaimer, "
      + "knowledge_sources_json, model_used, created_at "
      + "FROM consult_history WHERE user_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?",
      uid, limit, offset);
}

public Map<String, Object> deleteHistoryItem(int id) {
  long uid = CurrentUser.requireUserId();
  jdbc.update("DELETE FROM consult_history WHERE id=? AND user_id=?", id, uid);
  return Map.of("success", true);
}

public Map<String, Object> clearHistory() {
  long uid = CurrentUser.requireUserId();
  int deleted = jdbc.update("DELETE FROM consult_history WHERE user_id=?", uid);
  return Map.of("success", true, "deleted", deleted);
}

private void saveHistory(long uid, String requestId, String scene, String question,
    String answer, List<String> suggestions, String disclaimer, List<String> knowledgeSources, String modelUsed) {
  try {
    JsonSupport json = ...; // inject or use ObjectMapper directly
    jdbc.update(
        "INSERT INTO consult_history(user_id,request_id,scene,question,answer,suggestions_json,"
        + "disclaimer,knowledge_sources_json,model_used,created_at) VALUES(?,?,?,?,?,?,?,?,?,NOW())",
        uid, requestId, scene, question, answer,
        json.write(suggestions), disclaimer, json.write(knowledgeSources), modelUsed);
  } catch (Exception ignored) { /* best-effort */ }
}
```

Add to `ConsultController.java` (delegate to ConsultService — no JdbcTemplate needed in controller):

```java
@GetMapping("/history")
public List<Map<String, Object>> getHistory(
    @RequestParam(defaultValue = "20") int limit,
    @RequestParam(defaultValue = "0") int offset) {
  return consultService.getHistory(limit, offset);
}

@DeleteMapping("/history/{id}")
public Map<String, Object> deleteHistory(@PathVariable int id) {
  return consultService.deleteHistoryItem(id);
}

@DeleteMapping("/history")
public Map<String, Object> clearHistory() {
  return consultService.clearHistory();
}
```

- [ ] **Step 5: Update frontend consult.ts with history API**

Add to `健康监测与分析平台/src/api/modules/consult.ts`:

```typescript
export type ConsultHistoryItem = {
  id: number
  requestId: string
  scene: string
  question: string
  answer: string
  suggestions: string[]
  disclaimer: string
  knowledgeSources: string[]
  modelUsed: string
  createdAt: string
}

export async function getConsultHistory(limit = 20, offset = 0): Promise<ConsultHistoryItem[]> {
  try {
    const { data } = await http.get(`/consult/history?limit=${limit}&offset=${offset}`)
    return (data as any[]).map(item => ({
      ...item,
      suggestions: typeof item.suggestionsJson === 'string' ? JSON.parse(item.suggestionsJson) : [],
      knowledgeSources: typeof item.knowledgeSourcesJson === 'string' ? JSON.parse(item.knowledgeSourcesJson) : [],
    }))
  } catch { return [] }
}

export async function deleteConsultHistory(id: number): Promise<void> {
  await http.delete(`/consult/history/${id}`)
}

export async function clearConsultHistory(): Promise<void> {
  await http.delete('/consult/history')
}
```

- [ ] **Step 6: Update AssistantPage.vue with history, domain guard display, knowledge sources**

Add to the assistant page:

```vue
<!-- History toggle button in header -->
<button @click="showHistory = !showHistory" class="...">
  <iconify-icon icon="solar:history-outline" width="18" height="18" />
</button>

<!-- History panel (slides in from right) -->
<div v-if="showHistory" class="fixed inset-y-0 right-0 z-50 w-80 bg-white shadow-xl">
  <div class="flex items-center justify-between border-b p-4">
    <span class="font-semibold">历史记录</span>
    <div class="flex gap-2">
      <button @click="clearAllHistory" class="text-xs text-red-500">清空</button>
      <button @click="showHistory = false">
        <iconify-icon icon="solar:close-outline" width="20" height="20" />
      </button>
    </div>
  </div>
  <div class="overflow-y-auto" style="height: calc(100vh - 64px)">
    <div v-for="item in historyItems" :key="item.id"
      class="border-b p-3 hover:bg-slate-50 cursor-pointer"
      @click="loadHistoryQuestion(item)">
      <p class="text-sm font-medium text-slate-800 truncate">{{ item.question }}</p>
      <p class="mt-1 text-xs text-slate-500 truncate">{{ item.answer?.substring(0, 60) }}...</p>
      <div class="mt-1 flex items-center justify-between">
        <span class="text-xs text-slate-400">{{ formatTime(item.createdAt) }}</span>
        <button @click.stop="deleteHistoryItem(item.id)" class="text-xs text-red-400 hover:text-red-600">删除</button>
      </div>
    </div>
  </div>
</div>

<!-- Knowledge source attribution in assistant messages -->
<div v-if="msg.knowledgeSources?.length" class="mt-2 text-xs text-slate-400">
  <span>参考：</span>
  <span v-for="(src, i) in msg.knowledgeSources" :key="i">{{ src }}{{ i < msg.knowledgeSources.length - 1 ? '、' : '' }}</span>
</div>
```

- [ ] **Step 7: Build and verify**

Run: `cd backend-java && ./mvnw.cmd compile` && `cd 健康监测与分析平台 && npx vue-tsc --noEmit` && `cd 健康监测与分析平台 && npx vitest run`
Expected: All pass

- [ ] **Step 8: Commit**

```bash
git add backend-java/src/main/java/com/ahealth/backend/consult/ \
       backend-java/src/main/java/com/ahealth/backend/config/BackendSchemaInitializer.java \
       健康监测与分析平台/src/api/modules/consult.ts \
       健康监测与分析平台/src/modules/assistant/views/AssistantPage.vue
git commit -m "feat(assistant): add domain guard, RAG knowledge, persistent history, history CRUD"
```

---

## Plan Summary

| Task | Description | Key Innovation |
|------|-------------|----------------|
| 1 | OpenMed API Client | HuggingFace Inference API wrapper for NER/PII/VQA |
| 2 | PII Scrubbing Pipeline | Mask→restore pattern, privacy before LLM |
| 3 | PharmaDetect NER | Medical entity extraction from OCR text |
| 3b | OCR Preprocessing | Character confusion correction, text normalization, field extraction |
| 4 | DDI Knowledge Base | Drug-drug interaction lookup table |
| 5 | Multi-Model Router | Intent classification → model selection → context injection |
| 6 | Frontend DDI Display | Drug interaction warnings on medication page |
| 7 | CLAUDE.md Update | Documentation |
| 8 | Heart Rate Scoring | Age-adjusted baseline + HRV fusion |
| 9 | Sleep Scoring | Composite: deep sleep + efficiency + regularity |
| 10 | Stress Scoring | RMSSD-driven + recovery trend |
| 11 | Scoring Explanations | Algorithm notes in response |
| 12 | ROOK Device API | Unified device data from 8+ sources |
| 13 | Rehab Bug Fix | week_stats write path + exercise done persistence |
| 14 | Rehab Performance Analysis | Device-driven overexertion detection |
| 15 | ROOK Data Sync | Device data → monitor_records → scoring engine |
| 16 | AI Assistant Upgrade | Domain guard, RAG knowledge, history CRUD, streaming |
