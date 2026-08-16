package com.ahealth.backend.knowledge;

import com.ahealth.backend.common.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 健康知识图谱 REST 端点（13 个）。
 *
 * <p>覆盖中药材搜索、药品临床信息、方剂 CRUD、多药材识别、交互检查、用药间隔、过敏管理。
 */
@RestController
@RequestMapping("/api")
public class KnowledgeController {
  private final DrugKnowledgeService drugKnowledgeService;
  private final TcmFormulaService tcmFormulaService;
  private final MultiHerbRecognitionService multiHerbRecognitionService;
  private final InteractionCheckService interactionCheckService;
  private final DosingIntervalService dosingIntervalService;

  public KnowledgeController(
      DrugKnowledgeService drugKnowledgeService,
      TcmFormulaService tcmFormulaService,
      MultiHerbRecognitionService multiHerbRecognitionService,
      InteractionCheckService interactionCheckService,
      DosingIntervalService dosingIntervalService
  ) {
    this.drugKnowledgeService = drugKnowledgeService;
    this.tcmFormulaService = tcmFormulaService;
    this.multiHerbRecognitionService = multiHerbRecognitionService;
    this.interactionCheckService = interactionCheckService;
    this.dosingIntervalService = dosingIntervalService;
  }

  // ===== 中药材搜索 =====

  @GetMapping("/knowledge/herbs/search")
  public List<KnowledgeDtos.HerbSearchItem> searchHerbs(
      @RequestParam(name = "keyword", defaultValue = "") String keyword,
      @RequestParam(name = "limit", defaultValue = "20") int limit
  ) {
    return drugKnowledgeService.searchHerbs(keyword, limit);
  }

  @GetMapping("/knowledge/herbs/{name}")
  public ResponseEntity<KnowledgeDtos.HerbSearchItem> getHerb(@PathVariable String name) {
    return drugKnowledgeService.getHerbByName(name)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  // ===== 药品临床信息 =====

  @GetMapping("/knowledge/clinical/{drugName}")
  public ResponseEntity<KnowledgeDtos.ClinicalInfoResponse> getClinicalInfo(@PathVariable String drugName) {
    KnowledgeDtos.ClinicalInfoResponse info = drugKnowledgeService.getClinicalInfo(drugName);
    if (info == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(info);
  }

  // ===== 方剂 CRUD =====

  @PostMapping("/knowledge/formulas")
  public KnowledgeDtos.FormulaResponse createFormula(@RequestBody KnowledgeDtos.FormulaSaveRequest request) {
    long userId = CurrentUser.requireUserId();
    return tcmFormulaService.createFormula(userId, request);
  }

  @GetMapping("/knowledge/formulas")
  public List<KnowledgeDtos.FormulaListItem> listFormulas() {
    long userId = CurrentUser.requireUserId();
    return tcmFormulaService.listFormulas(userId);
  }

  @GetMapping("/knowledge/formulas/{id}")
  public KnowledgeDtos.FormulaResponse getFormula(@PathVariable long id) {
    return tcmFormulaService.getFormula(id);
  }

  @DeleteMapping("/knowledge/formulas/{id}")
  public Map<String, Boolean> deleteFormula(@PathVariable long id) {
    long userId = CurrentUser.requireUserId();
    boolean ok = tcmFormulaService.deleteFormula(id, userId);
    return Map.of("deleted", ok);
  }

  // ===== 多药材识别 =====

  @PostMapping("/knowledge/herbs/recognize")
  public KnowledgeDtos.HerbRecognitionResult recognizeHerbs(
      @RequestParam("file") MultipartFile file
  ) {
    return multiHerbRecognitionService.recognize(file);
  }

  // ===== 交互检查 =====

  @GetMapping("/knowledge/interactions")
  public KnowledgeDtos.InteractionReport getInteractionReport() {
    return interactionCheckService.checkAll();
  }

  // ===== 用药间隔 =====

  @GetMapping("/knowledge/dosing-schedule")
  public KnowledgeDtos.DosingSchedule getDosingSchedule() {
    return dosingIntervalService.arrange();
  }

  // ===== 过敏管理 =====

  @GetMapping("/knowledge/allergies")
  public List<KnowledgeDtos.AllergyItem> listAllergies() {
    return interactionCheckService.getUserAllergies();
  }

  @PostMapping("/knowledge/allergies")
  public KnowledgeDtos.AllergyItem addAllergy(@RequestBody KnowledgeDtos.AllergySaveRequest request) {
    return interactionCheckService.addAllergy(request);
  }

  @DeleteMapping("/knowledge/allergies/{id}")
  public Map<String, Boolean> removeAllergy(@PathVariable long id) {
    boolean ok = interactionCheckService.removeAllergy(id);
    return Map.of("deleted", ok);
  }
}
