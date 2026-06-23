package com.ahealth.backend.rehab;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rehab")
public class RehabController {
  private final RehabService rehabService;

  public RehabController(RehabService rehabService) {
    this.rehabService = rehabService;
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
}
