package com.ahealth.backend.medication;

import com.ahealth.backend.ai.AiDtos;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api")
public class MedicationController {
  private final MedicationService medicationService;

  public MedicationController(MedicationService medicationService) {
    this.medicationService = medicationService;
  }

  @GetMapping("/medications")
  public List<MedicationDtos.MedicationItem> medications() {
    return medicationService.listMedications();
  }

  @PostMapping("/medications")
  public MedicationDtos.MedicationItem createMedication(@RequestBody MedicationDtos.MedicationSaveRequest request) {
    return medicationService.createMedication(request);
  }

  @PutMapping("/medications/{id}")
  public MedicationDtos.MedicationItem updateMedication(
      @PathVariable long id,
      @RequestBody MedicationDtos.MedicationSaveRequest request
  ) {
    return medicationService.updateMedication(id, request);
  }

  @PostMapping("/medications/{id}/toggle")
  public Map<String, Object> toggleMedication(@PathVariable long id) {
    return medicationService.toggleMedication(id);
  }

  @DeleteMapping("/medications/{id}")
  public Map<String, Boolean> deleteMedication(@PathVariable long id) {
    return medicationService.deleteMedication(id);
  }

  @GetMapping("/medication-alarms")
  public List<MedicationDtos.MedicationAlarm> alarms() {
    return medicationService.listAlarms();
  }

  @PostMapping("/medication-alarms")
  public MedicationDtos.MedicationAlarm createAlarm(@RequestBody MedicationDtos.MedicationAlarmSaveRequest request) {
    return medicationService.createAlarm(request);
  }

  @PutMapping("/medication-alarms/{id}")
  public MedicationDtos.MedicationAlarm updateAlarm(
      @PathVariable long id,
      @RequestBody MedicationDtos.MedicationAlarmSaveRequest request
  ) {
    return medicationService.updateAlarm(id, request);
  }

  @PostMapping("/medication-alarms/{id}/toggle")
  public Map<String, Object> toggleAlarm(@PathVariable long id) {
    return medicationService.toggleAlarm(id);
  }

  @DeleteMapping("/medication-alarms/{id}")
  public Map<String, Boolean> deleteAlarm(@PathVariable long id) {
    return medicationService.deleteAlarm(id);
  }

  @PostMapping("/medications/recognize")
  public MedicationDtos.MedicationRecognitionBatchResult recognize(
      @RequestParam("files") MultipartFile[] files
  ) {
    return medicationService.recognizeByModel(files);
  }

  @PostMapping("/medications/recognize/custom-model")
  public MedicationDtos.MedicationRecognitionBatchResult recognizeByCustomModel(
      @RequestParam("files") MultipartFile[] files
  ) {
    return medicationService.recognizeByCustomModel(files);
  }

  @PostMapping("/medications/explain")
  public MedicationDtos.MedicationExplainResponse explain(@RequestBody MedicationDtos.MedicationExplainRequest request) {
    return medicationService.explainMedication(request);
  }

  @PostMapping("/medications/confirm-intake")
  public Map<String, Object> confirmIntake(@RequestBody MedicationDtos.MedicationIntakeConfirmRequest request) {
    return medicationService.confirmIntake(request);
  }

  @GetMapping("/medications/today")
  public MedicationDtos.TodayScheduleResponse todaySchedule() {
    return medicationService.getTodaySchedule();
  }

  @GetMapping("/medications/interactions")
  public List<AiDtos.DdiWarning> checkInteractions() {
    return medicationService.checkDrugInteractions();
  }
}
