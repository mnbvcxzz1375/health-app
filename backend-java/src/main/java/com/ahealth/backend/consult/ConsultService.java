package com.ahealth.backend.consult;

import com.ahealth.backend.ai.AiDtos;
import com.ahealth.backend.ai.AnswerTemplateService;
import com.ahealth.backend.ai.DashScopeService;
import com.ahealth.backend.ai.LlmCacheService;
import com.ahealth.backend.ai.ModelRouterService;
import com.ahealth.backend.ai.PiiScrubService;
import com.ahealth.backend.ai.PromptTemplateService;
import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import com.ahealth.backend.context.ContextDtos;
import com.ahealth.backend.context.ContextService;
import com.ahealth.backend.rag.RagDtos;
import com.ahealth.backend.rag.RagSearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConsultService {
  private static final Logger log = LoggerFactory.getLogger(ConsultService.class);
  private static final Duration CACHE_TTL = Duration.ofHours(168); // 7 天

  private final DashScopeService dashScopeService;
  private final ModelRouterService modelRouterService;
  private final PiiScrubService piiScrubService;
  private final ContextService contextService;
  private final HealthKnowledgeService knowledgeService;
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final PromptTemplateService promptTemplateService;
  private final AnswerTemplateService answerTemplateService;
  private final LlmCacheService llmCacheService;
  private final RagSearchService ragSearchService;
  private final ConsultSafetyService safetyService;

  public ConsultService(DashScopeService dashScopeService, ModelRouterService modelRouterService,
      PiiScrubService piiScrubService, ContextService contextService,
      HealthKnowledgeService knowledgeService, JdbcTemplate jdbc, ObjectMapper objectMapper,
      PromptTemplateService promptTemplateService,
      AnswerTemplateService answerTemplateService,
      LlmCacheService llmCacheService,
      RagSearchService ragSearchService,
      ConsultSafetyService safetyService) {
    this.dashScopeService = dashScopeService;
    this.modelRouterService = modelRouterService;
    this.piiScrubService = piiScrubService;
    this.contextService = contextService;
    this.knowledgeService = knowledgeService;
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.promptTemplateService = promptTemplateService;
    this.answerTemplateService = answerTemplateService;
    this.llmCacheService = llmCacheService;
    this.ragSearchService = ragSearchService;
    this.safetyService = safetyService;
  }

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
    String scene = normalizeScene(request.scene());
    ConsultDtos.SafetyInfo initialSafety = safetyService.assess(question);

    if (safetyService.requiresUrgentEscalation(initialSafety)) {
      String requestId = "consult_safety_" + UUID.randomUUID().toString().replace("-", "");
      String answer = "你描述的情况可能涉及需要紧急处理的警示信号。请不要继续训练、不要自行增减或停用处方药，请立即寻求线下急诊或急救帮助。";
      String disclaimer = "此提示仅用于识别可能的紧急风险，不能替代医疗机构的诊断和处置。";
      saveHistory(uid, requestId, scene, question, answer, List.of(), disclaimer,
          List.of(), "safety_guard");
      return new ConsultDtos.ConsultResponse(requestId, answer, List.of(), disclaimer,
          List.of(), initialSafety);
    }

    // === Layer 1: 答案模板（高频问题秒级响应，0 LLM 调用、0 RAG 检索） ===
    try {
      var templated = answerTemplateService.tryAnswer(question, "consult");
      if (templated.isPresent()) {
        String requestId = "consult_" + UUID.randomUUID().toString().replace("-", "");
        String answer = normalizeText(templated.get(), "");
        List<String> suggestions = defaultSuggestions();
        String disclaimer = "以上内容仅用于健康管理辅助，不替代医生诊疗。";
        asyncSaveMemory(requestId, question, answer);
        ConsultDtos.SafetyInfo safety = addNoEvidenceFlag(initialSafety);
        saveHistoryWithAudit(uid, requestId, scene, question, answer, suggestions, disclaimer,
            List.of(), safety, "answer_template");
        log.info("[Consult] 模板命中: requestId={} question='{}'", requestId, truncate(question, 50));
        return new ConsultDtos.ConsultResponse(requestId, answer, suggestions, disclaimer,
            List.of(), safety);
      }
    } catch (Exception e) {
      log.warn("[Consult] 答案模板查询失败，跳过: {}", e.getMessage());
    }

    // === Layer 2: 缓存命中检查（0 LLM 调用） ===
    String contextHash = buildContextHash(uid);
    try {
      var cached = llmCacheService.getExact("consult", question, contextHash);
      if (cached.isPresent()) {
        String cachedJson = cached.get();
        try {
          JsonNode payload = objectMapper.readTree(cachedJson);
          String requestId = "consult_" + UUID.randomUUID().toString().replace("-", "");
          String answer = normalizeText(payload.path("answer").asText(""),
              "建议先结合近期监测趋势、症状变化和康复安排综合判断。");
          List<String> suggestions = normalizeSuggestions(payload.path("suggestions"));
          String disclaimer = normalizeText(payload.path("disclaimer").asText(""),
              "以上内容仅用于健康管理辅助，不替代医生诊疗。");
          List<ConsultDtos.ConsultEvidence> evidence = parseEvidence(payload.path("evidence"));
          ConsultDtos.SafetyInfo safety = parseSafety(payload.path("safety"), initialSafety, evidence);
          asyncSaveMemory(requestId, question, answer);
          saveHistoryWithAudit(uid, requestId, scene, question, answer, suggestions, disclaimer,
              evidence, safety, "llm_cache");
          log.info("[Consult] 缓存命中: requestId={} question='{}'", requestId, truncate(question, 50));
          return new ConsultDtos.ConsultResponse(requestId, answer, suggestions, disclaimer,
              evidence, safety);
        } catch (JsonProcessingException e) {
          log.warn("[Consult] 缓存 JSON 解析失败，继续走正常流程: {}", e.getMessage());
        }
      }
    } catch (Exception e) {
      log.warn("[Consult] 缓存查询失败，跳过: {}", e.getMessage());
    }

    // === Layer 3: RAG 检索（优先 RAG，失败 fallback 到 knowledgeService） ===
    KnowledgeRetrieval retrieval = retrieveKnowledgeWithRagFallback(uid, question);
    List<String> knowledge = retrieval.knowledge();
    String knowledgeBlock = "";
    if (!knowledge.isEmpty()) {
      knowledgeBlock = "\n相关知识：\n" + String.join("\n", knowledge.stream().map(k -> "- " + k).toList());
    }

    // Build context-aware message with knowledge injection
    String userMessage = buildContextAwareMessage(scene, question) + knowledgeBlock;

    // === Layer 4: 路由（ModelRouterService 内部走 Agent 或 DashScope） ===
    String routedJson;
    String modelUsed = "router";
    try {
      routedJson = modelRouterService.routeHealthQuestion(userMessage, scene);
    } catch (Exception e) {
      // Fallback: direct DashScope call if router fails
      log.warn("[Consult] ModelRouter 失败，fallback 到 DashScope: {}", e.getMessage());
      AiDtos.PiiScrubResult scrubResult = piiScrubService.scrub(userMessage);
      JsonNode payload = dashScopeService.requestJson(
          promptTemplateService.render("consult.assistant_system", Map.of()), scrubResult.scrubbedText(),
          dashScopeService.chatModel(), 0.35, "智能助手");
      var node = objectMapper.createObjectNode();
      node.put("answer", piiScrubService.restore(payload.path("answer").asText(""), scrubResult.masks()));
      node.putArray("suggestions");
      node.put("disclaimer", payload.path("disclaimer").asText("仅用于健康管理辅助。"));
      try {
        routedJson = objectMapper.writeValueAsString(node);
      } catch (JsonProcessingException ex) {
        routedJson = "{\"answer\":\"\",\"suggestions\":[],\"disclaimer\":\"仅用于健康管理辅助。\"}";
      }
      modelUsed = "dashscope_fallback";
    }

    JsonNode payload;
    try {
      payload = objectMapper.readTree(routedJson);
    } catch (JsonProcessingException e) {
      var node = objectMapper.createObjectNode();
      node.put("answer", routedJson);
      node.putArray("suggestions");
      node.put("disclaimer", "仅用于健康管理辅助。");
      payload = node;
    }

    String answer = normalizeText(payload.path("answer").asText(""),
        "建议先结合近期监测趋势、症状变化和康复安排综合判断。");
    List<String> suggestions = normalizeSuggestions(payload.path("suggestions"));
    String disclaimer = normalizeText(
        payload.path("disclaimer").asText(""),
        "以上内容仅用于健康管理辅助，不替代医生诊疗。"
    );
    // Merge model metadata conservatively: a model response may add detail,
    // but it must never downgrade deterministic safety rules or hide missing evidence.
    ConsultDtos.SafetyInfo safety = parseSafety(payload.path("safety"), initialSafety,
        retrieval.evidence());

    String requestId = "consult_" + UUID.randomUUID().toString().replace("-", "");
    asyncSaveMemory(requestId, question, answer);

    // === Layer 5: 写缓存（best-effort） ===
    try {
      String cachePayload = objectMapper.writeValueAsString(Map.of(
          "answer", answer,
          "suggestions", suggestions,
          "disclaimer", disclaimer,
          "evidence", retrieval.evidence(),
          "safety", safety
      ));
      llmCacheService.put("consult", question, contextHash, cachePayload, CACHE_TTL, true);
    } catch (Exception e) {
      log.debug("[Consult] 写缓存失败: {}", e.getMessage());
    }

    // Persist to consult_history
    saveHistoryWithAudit(uid, requestId, scene, question, answer, suggestions, disclaimer,
        retrieval.evidence(), safety, modelUsed);

    return new ConsultDtos.ConsultResponse(requestId, answer, suggestions, disclaimer,
        retrieval.evidence(), safety);
  }

  /**
   * RAG 优先检索；失败或空结果时 fallback 到 {@link HealthKnowledgeService#retrieveKnowledge}。
   */
  private KnowledgeRetrieval retrieveKnowledgeWithRagFallback(long uid, String question) {
    try {
      List<RagDtos.RagSearchHit> hits = ragSearchService.search(question, null, 5);
      if (hits != null && !hits.isEmpty()) {
        List<String> result = new ArrayList<>();
        List<ConsultDtos.ConsultEvidence> evidence = new ArrayList<>();
        for (RagDtos.RagSearchHit hit : hits) {
          if (hit.chunkText() != null && !hit.chunkText().isBlank()) {
            result.add(hit.chunkText().trim());
            evidence.add(toEvidence(hit));
          }
        }
        if (!result.isEmpty()) {
          return new KnowledgeRetrieval(result, evidence);
        }
      }
    } catch (Exception e) {
      log.debug("[Consult] RAG 检索失败，fallback 到 knowledgeService: {}", e.getMessage());
    }
    try {
      List<String> fallback = knowledgeService.retrieveKnowledge(uid, question);
      List<ConsultDtos.ConsultEvidence> evidence = new ArrayList<>();
      for (int index = 0; index < fallback.size(); index++) {
        String chunk = fallback.get(index);
        if (chunk != null && !chunk.isBlank()) {
          evidence.add(new ConsultDtos.ConsultEvidence(
              "legacy_knowledge_" + index,
              "健康知识库（兼容检索）",
              "legacy_knowledge",
              "",
              truncate(chunk.trim(), 240),
              null
          ));
        }
      }
      return new KnowledgeRetrieval(fallback, evidence);
    } catch (Exception e) {
      log.debug("[Consult] knowledgeService 检索失败: {}", e.getMessage());
      return new KnowledgeRetrieval(List.of(), List.of());
    }
  }

  private ConsultDtos.ConsultEvidence toEvidence(RagDtos.RagSearchHit hit) {
    String field = hit.metadata() == null ? "" : hit.metadata().getOrDefault("field_name", "");
    return new ConsultDtos.ConsultEvidence(
        hit.redisDocId(),
        normalizeText(hit.title(), "健康知识片段"),
        readableDocType(hit.docType()),
        field,
        truncate(hit.chunkText().trim(), 240),
        Math.round(hit.score() * 10_000d) / 10_000d
    );
  }

  private String readableDocType(String docType) {
    return switch (docType == null ? "" : docType) {
      case "drug_label" -> "药品说明";
      case "ddi_rule" -> "药物相互作用规则";
      case "rehab_guide" -> "康复动作指南";
      case "food_guide" -> "营养知识";
      case "herb_guide" -> "中药知识";
      case "tcm_incompat" -> "中药配伍禁忌";
      case "tcm_wm" -> "中西药相互作用";
      case "drug_food" -> "药食相互作用";
      case "consult_qa" -> "历史健康问答";
      default -> "健康知识";
    };
  }

  private ConsultDtos.SafetyInfo addNoEvidenceFlag(ConsultDtos.SafetyInfo safety) {
    List<String> flags = new ArrayList<>(safety.flags());
    if (!flags.contains("NO_RETRIEVED_EVIDENCE")) flags.add("NO_RETRIEVED_EVIDENCE");
    List<String> actionTags = new ArrayList<>(safety.actionTags());
    addTag(actionTags, "NO_PERSONALIZED_GUIDANCE");
    addTag(actionTags, "REQUEST_MORE_EVIDENCE");
    return new ConsultDtos.SafetyInfo(
        "emergency".equalsIgnoreCase(safety.level()) ? "emergency" : "uncertain", flags,
        "本次回答未检索到可展示的知识证据，请不要仅据此调整训练或用药。",
        "emergency".equalsIgnoreCase(safety.level()) ? "emergency" : "uncertain",
        actionTags
    );
  }

  private List<ConsultDtos.ConsultEvidence> parseEvidence(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    List<ConsultDtos.ConsultEvidence> evidence = new ArrayList<>();
    for (JsonNode item : node) {
      try {
        ConsultDtos.ConsultEvidence parsed = objectMapper.treeToValue(item, ConsultDtos.ConsultEvidence.class);
        if (parsed != null && parsed.id() != null && !parsed.id().isBlank()) evidence.add(parsed);
      } catch (JsonProcessingException ignored) {
      }
    }
    return evidence;
  }

  private void addTag(List<String> tags, String tag) {
    if (!tags.contains(tag)) tags.add(tag);
  }

  ConsultDtos.SafetyInfo parseSafety(JsonNode node, ConsultDtos.SafetyInfo fallback,
      List<ConsultDtos.ConsultEvidence> evidence) {
    ConsultDtos.SafetyInfo candidate = null;
    if (node != null && node.isObject()) {
      try {
        ConsultDtos.SafetyInfo parsed = objectMapper.treeToValue(node, ConsultDtos.SafetyInfo.class);
        if (parsed != null && parsed.level() != null) candidate = parsed;
      } catch (JsonProcessingException ignored) {
      }
    }
    ConsultDtos.SafetyInfo merged = mergeSafety(fallback, candidate);
    return evidence.isEmpty() ? addNoEvidenceFlag(merged) : merged;
  }

  /** Merge model metadata without allowing a lower-severity value to overwrite deterministic rules. */
  ConsultDtos.SafetyInfo mergeSafety(ConsultDtos.SafetyInfo deterministic,
      ConsultDtos.SafetyInfo candidate) {
    ConsultDtos.SafetyInfo base = deterministic == null
        ? new ConsultDtos.SafetyInfo("routine", List.of(), "", "routine_review", List.of())
        : deterministic;
    if (candidate == null) return base;

    List<String> flags = new ArrayList<>(base.flags());
    candidate.flags().forEach(flag -> addTag(flags, flag));
    List<String> actionTags = new ArrayList<>(base.actionTags());
    candidate.actionTags().forEach(tag -> addTag(actionTags, tag));
    String level = severityRank(candidate.level()) > severityRank(base.level())
        ? candidate.level() : base.level();
    String escalation = escalationRank(candidate.escalation()) > escalationRank(base.escalation())
        ? candidate.escalation() : base.escalation();
    if ("emergency".equalsIgnoreCase(level)) {
      addTag(actionTags, "STOP_ACTIVITY");
      addTag(actionTags, "EMERGENCY_CARE");
      addTag(actionTags, "NO_DOSAGE_INFERENCE");
      escalation = "emergency";
    }
    String uncertainty = candidate.uncertainty() == null || candidate.uncertainty().isBlank()
        ? base.uncertainty() : candidate.uncertainty();
    return new ConsultDtos.SafetyInfo(level, flags, uncertainty, escalation, actionTags);
  }

  private int severityRank(String level) {
    return switch (level == null ? "" : level.trim().toLowerCase()) {
      case "emergency", "critical" -> 3;
      case "elevated", "high" -> 2;
      case "uncertain" -> 1;
      default -> 0;
    };
  }

  private int escalationRank(String escalation) {
    return switch (escalation == null ? "" : escalation.trim().toLowerCase()) {
      case "emergency" -> 3;
      case "clinician_review", "professional_review" -> 2;
      case "uncertain" -> 1;
      default -> 0;
    };
  }

  private record KnowledgeRetrieval(List<String> knowledge, List<ConsultDtos.ConsultEvidence> evidence) {}

  /**
   * 构造 contextHash：基于用户画像 + 当前用药的稳定哈希，用于区分不同用户上下文的相同 prompt。
   */
  private String buildContextHash(long uid) {
    try {
      ContextDtos.ContextSnapshot ctx = contextService.getSnapshot();
      String summary = ctx.systemSummary() == null ? "" : ctx.systemSummary();
      String meds = ctx.currentMedications() == null ? "" : String.join(",", ctx.currentMedications());
      String raw = summary + "|" + meds;
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      return "";
    }
  }

  private List<String> defaultSuggestions() {
    return List.of("我今天适合做什么强度的训练？", "最近睡眠一般，今晚怎么调整？", "上传报告后应该先看哪些风险提示？");
  }

  private String buildContextAwareMessage(String scene, String question) {
    StringBuilder sb = new StringBuilder();
    sb.append("场景：").append(normalizeScene(scene)).append("\n");

    try {
      ContextDtos.ContextSnapshot ctx = contextService.getSnapshot();

      if (ctx.systemSummary() != null && !ctx.systemSummary().isBlank()) {
        sb.append("用户画像：").append(ctx.systemSummary()).append("\n");
      }

      if (ctx.dailySummary() != null && !ctx.dailySummary().isBlank()) {
        sb.append("今日监测：").append(ctx.dailySummary()).append("\n");
      }

      if (ctx.activeConcerns() != null && !ctx.activeConcerns().isEmpty()) {
        sb.append("当前关注：").append(String.join("；", ctx.activeConcerns())).append("\n");
      }

      if (ctx.currentMedications() != null && !ctx.currentMedications().isEmpty()) {
        sb.append("当前用药：").append(String.join("、", ctx.currentMedications())).append("\n");
      }

      if (ctx.memories() != null && !ctx.memories().isEmpty()) {
        List<String> recentMemories = ctx.memories().stream()
            .limit(3)
            .map(ContextDtos.MemoryEntry::content)
            .toList();
        sb.append("历史记忆：").append(String.join("；", recentMemories)).append("\n");
      }

      ContextDtos.PatientMemoryBrief patientMemory = ctx.patientMemory();
      if (patientMemory != null) {
        appendPatientMemory(sb, "已确认长期健康记忆", patientMemory.longTerm());
        appendPatientMemory(sb, "康复周期记忆", patientMemory.careCycle());
        appendPatientMemory(sb, "近期会话记忆", patientMemory.encounter());
      }
    } catch (Exception ignored) {
      // Context unavailable — proceed without context
    }

    sb.append("问题：").append(question);
    return sb.toString();
  }

  private void asyncSaveMemory(String requestId, String question, String answer) {
    try {
      String memoryContent = "用户问：" + truncate(question, 80) + " → 回答要点：" + truncate(answer, 120);
      contextService.saveMemory(new ContextDtos.SaveMemoryRequest("consult", memoryContent));
      contextService.recordConsultSummary(question, answer);
    } catch (Exception ignored) {
      // Memory save is best-effort
    }
  }

  private void appendPatientMemory(StringBuilder sb, String heading,
      List<ContextDtos.PatientMemoryItem> items) {
    if (items == null || items.isEmpty()) return;
    sb.append(heading).append("：");
    sb.append(String.join("；", items.stream()
        .map(item -> "[" + item.memoryType() + "] " + item.content())
        .toList()));
    sb.append("\n");
  }

  private String truncate(String text, int maxLen) {
    if (text == null) return "";
    return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
  }

  private String normalizeScene(String scene) {
    String text = scene == null ? "" : scene.trim();
    return text.isBlank() ? "assistant" : text;
  }

  private List<String> normalizeSuggestions(JsonNode node) {
    if (!node.isArray()) {
      return List.of("我今天适合做什么强度的训练？", "最近睡眠一般，今晚怎么调整？", "上传报告后应该先看哪些风险提示？");
    }
    List<String> values = new ArrayList<>();
    for (JsonNode item : node) {
      String text = normalizeText(item.asText(""), "");
      if (!text.isBlank() && !looksLikeDisclaimer(text)) {
        values.add(text);
      }
    }
    if (values.size() < 3) {
      return List.of("我今天适合做什么强度的训练？", "最近睡眠一般，今晚怎么调整？", "上传报告后应该先看哪些风险提示？");
    }
    return values.subList(0, Math.min(3, values.size()));
  }

  private boolean looksLikeDisclaimer(String text) {
    return text.contains("本建议") || text.contains("非医疗") || text.contains("遵医嘱")
        || text.contains("不替代") || text.contains("仅供参考") || text.contains("请咨询医生")
        || text.contains("专业医疗") || text.contains("医疗诊断") || text.contains("咨询医生");
  }

  private String normalizeText(String value, String fallback) {
    String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
    return text.isBlank() ? fallback : text;
  }

  // === History CRUD ===

  public List<Map<String, Object>> getHistory(int limit, int offset) {
    long uid = CurrentUser.requireUserId();
    return jdbc.queryForList(
        "SELECT id, request_id, scene, question, answer, suggestions_json, disclaimer, "
        + "knowledge_sources_json, evidence_json, safety_json, model_used, created_at "
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
      String answer, List<String> suggestions, String disclaimer,
      List<String> knowledgeSources, String modelUsed) {
    try {
      jdbc.update(
          "INSERT INTO consult_history(user_id,request_id,scene,question,answer,suggestions_json,"
          + "disclaimer,knowledge_sources_json,model_used,created_at) VALUES(?,?,?,?,?,?,?,?,?,NOW())",
          uid, requestId, scene, question, answer,
          toJson(suggestions), disclaimer, toJson(knowledgeSources), modelUsed);
    } catch (Exception ignored) {
      // History save is best-effort
    }
  }

  private void saveHistoryWithAudit(long uid, String requestId, String scene, String question,
      String answer, List<String> suggestions, String disclaimer,
      List<ConsultDtos.ConsultEvidence> evidence, ConsultDtos.SafetyInfo safety, String modelUsed) {
    try {
      jdbc.update(
          "INSERT INTO consult_history(user_id,request_id,scene,question,answer,suggestions_json,"
          + "disclaimer,knowledge_sources_json,evidence_json,safety_json,model_used,created_at) "
          + "VALUES(?,?,?,?,?,?,?,?,?,?,?,NOW())",
          uid, requestId, scene, question, answer, toJson(suggestions), disclaimer,
          toJson(evidence.stream().map(ConsultDtos.ConsultEvidence::title).toList()),
          toJson(evidence), toJson(safety), modelUsed);
    } catch (Exception e) {
      log.debug("[Consult] 审计历史写入失败，降级为兼容历史记录: {}", e.getMessage());
      saveHistory(uid, requestId, scene, question, answer, suggestions, disclaimer,
          evidence.stream().map(ConsultDtos.ConsultEvidence::title).toList(), modelUsed);
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }
}
