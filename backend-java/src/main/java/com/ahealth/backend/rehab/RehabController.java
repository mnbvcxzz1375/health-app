package com.ahealth.backend.rehab;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ahealth.backend.common.CurrentUser;

@RestController
@RequestMapping("/api/rehab")
public class RehabController {
  private final RehabService rehabService;
  private final SmartRehabPlannerService smartRehabPlannerService;
  private final RehabCaseService rehabCaseService;

  public RehabController(RehabService rehabService, SmartRehabPlannerService smartRehabPlannerService,
      RehabCaseService rehabCaseService) {
    this.rehabService = rehabService;
    this.smartRehabPlannerService = smartRehabPlannerService;
    this.rehabCaseService = rehabCaseService;
  }

  @GetMapping("/plan")
  public RehabDtos.RehabPlanResponse plan() {
    return rehabService.getPlan();
  }

  @PostMapping("/plan/{id}/toggle")
  public RehabDtos.RehabPlanResponse toggle(@PathVariable long id) {
    return rehabService.togglePlanItem(id);
  }

  @DeleteMapping("/plan/{id}")
  public RehabDtos.RehabPlanResponse remove(@PathVariable long id) {
    return rehabService.removePlanItem(id);
  }

  @PostMapping("/plan/apply")
  public RehabDtos.RehabPlanResponse applyPlan(@RequestBody RehabDtos.RehabPlanDraft request) {
    return rehabService.applyPlanDraft(request);
  }

  @GetMapping("/exercises/by-name")
  public RehabDtos.RehabExercise exercise(@RequestParam String name) {
    return rehabService.getExerciseByName(name);
  }

  @GetMapping("/reminder")
  public RehabDtos.RehabReminderResponse reminder(@RequestParam String name) {
    return rehabService.getReminder(name);
  }

  @PostMapping("/reminder")
  public RehabDtos.RehabReminderResponse saveReminder(@RequestBody RehabDtos.SaveRehabReminderRequest request) {
    return rehabService.saveReminder(request);
  }

  @GetMapping("/plan/settings")
  public RehabDtos.RehabPlanSettingsResponse settings() {
    return rehabService.getPlanSettings();
  }

  @PostMapping("/plan/settings")
  public RehabDtos.RehabPlanSettingsResponse saveSettings(@RequestBody RehabDtos.RehabPlanSettingsResponse request) {
    return rehabService.savePlanSettings(request);
  }

  @GetMapping("/plan/reminder")
  public RehabDtos.PlanReminderDraft planReminder() {
    return rehabService.getPlanReminder();
  }

  @PostMapping("/plan/reminder")
  public RehabDtos.PlanReminderDraft savePlanReminder(@RequestBody RehabDtos.PlanReminderDraft request) {
    return rehabService.savePlanReminder(request);
  }

  @GetMapping("/analysis")
  public RehabDtos.RehabPerformanceAnalysis analyzePerformance() {
    return rehabService.analyzeRehabPerformance(CurrentUser.requireUserId());
  }

  /** 智能康复计划：根据身高/体重/年龄/性别/目标生成 BMR/TDEE + 推荐动作。 */
  @PostMapping("/smart-plan")
  public RehabDtos.SmartPlanResponse smartPlan(@RequestBody RehabDtos.SmartPlanRequest request) {
    return smartRehabPlannerService.generatePlan(request);
  }

  @GetMapping("/case")
  public RehabDtos.RehabCase currentCase() {
    return rehabCaseService.buildCurrentCase();
  }

  /** 应用智能计划为今日计划。 */
  @PostMapping("/smart-plan/apply")
  public RehabDtos.RehabPlanResponse applySmartPlan(@RequestBody Map<String, List<Long>> body) {
    long userId = CurrentUser.requireUserId();
    List<Long> exerciseIds = body.getOrDefault("exerciseIds", List.of());
    rehabService.applySmartPlan(userId, exerciseIds);
    return rehabService.getPlan();
  }

  /** 查询当前用户身体指标（BMI/BMR/TDEE/目标热量）。 */
  @GetMapping("/body-metrics")
  public RehabDtos.BodyMetrics bodyMetrics() {
    return rehabService.calculateBodyMetrics(CurrentUser.requireUserId());
  }
}
