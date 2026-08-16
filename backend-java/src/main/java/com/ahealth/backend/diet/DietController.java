package com.ahealth.backend.diet;

import com.ahealth.backend.common.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 饮食与食物识别 REST 端点。
 *
 * <p>GET  /api/diet/foods/search?keyword=&limit=
 * POST /api/diet/recognize (multipart image)
 * POST /api/diet/plan
 * GET  /api/diet/preferences
 * POST /api/diet/preferences
 * GET  /api/diet/logs/today
 * POST /api/diet/logs
 * PUT  /api/diet/logs/{id}
 * DELETE /api/diet/logs/{id}
 * GET  /api/diet/logs/{id}/audit
 */
@RestController
@RequestMapping("/api/diet")
public class DietController {
  private final FoodService foodService;
  private final DietService dietService;
  private final DietPreferenceService dietPreferenceService;
  private final FoodRecognitionService foodRecognitionService;
  private final DietLogService dietLogService;

  public DietController(
      FoodService foodService,
      DietService dietService,
      DietPreferenceService dietPreferenceService,
      FoodRecognitionService foodRecognitionService,
      DietLogService dietLogService
  ) {
    this.foodService = foodService;
    this.dietService = dietService;
    this.dietPreferenceService = dietPreferenceService;
    this.foodRecognitionService = foodRecognitionService;
    this.dietLogService = dietLogService;
  }

  @GetMapping("/foods/search")
  public List<DietDtos.FoodSearchItem> searchFoods(
      @RequestParam(name = "keyword", defaultValue = "") String keyword,
      @RequestParam(name = "limit", defaultValue = "20") int limit
  ) {
    return foodService.searchFoods(keyword, limit);
  }

  @PostMapping(value = "/recognize", consumes = "multipart/form-data")
  public DietDtos.FoodRecognitionResponse recognizeFood(
      @RequestParam("file") MultipartFile file
  ) {
    return foodRecognitionService.recognize(file);
  }

  @PostMapping("/plan")
  public DietDtos.DietPlanResponse generatePlan(@RequestBody DietDtos.DietPlanRequest request) {
    return dietService.generatePlan(CurrentUser.requireUserId(), request);
  }

  @GetMapping("/preferences")
  public DietDtos.DietPreference getPreference() {
    return dietPreferenceService.getPreference(CurrentUser.requireUserId());
  }

  @PostMapping("/preferences")
  public DietDtos.DietPreference savePreference(@RequestBody DietDtos.DietPreferenceSaveRequest request) {
    return dietPreferenceService.savePreference(CurrentUser.requireUserId(), request);
  }

  @GetMapping("/logs/today")
  public List<DietDtos.DietLogEntry> listTodayLogs() {
    return dietLogService.listToday(CurrentUser.requireUserId());
  }

  @PostMapping("/logs")
  public DietDtos.DietLogEntry saveLog(@RequestBody DietDtos.DietLogSaveRequest request) {
    return dietLogService.save(CurrentUser.requireUserId(), request);
  }

  @PutMapping("/logs/{id}")
  public DietDtos.DietLogEntry updateLog(
      @PathVariable long id,
      @RequestBody DietDtos.DietLogSaveRequest request
  ) {
    return dietLogService.update(CurrentUser.requireUserId(), id, request);
  }

  @DeleteMapping("/logs/{id}")
  public DietDtos.DietLogOperationResult deleteLog(@PathVariable long id) {
    return dietLogService.delete(CurrentUser.requireUserId(), id);
  }

  @GetMapping("/logs/{id}/audit")
  public List<DietDtos.DietLogAuditEntry> listLogAudit(@PathVariable long id) {
    return dietLogService.listAudit(CurrentUser.requireUserId(), id);
  }
}
