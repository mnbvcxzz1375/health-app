package com.ahealth.backend.context;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/context")
public class ContextController {
  private final ContextService service;
  private final PatientMemoryService patientMemoryService;
  public ContextController(ContextService service, PatientMemoryService patientMemoryService) {
    this.service = service;
    this.patientMemoryService = patientMemoryService;
  }

  @GetMapping("/snapshot")
  public ContextDtos.ContextSnapshot snapshot() { return service.getSnapshot(); }

  @PostMapping("/memory/save")
  public void saveMemory(@RequestBody ContextDtos.SaveMemoryRequest req) { service.saveMemory(req); }

  @PostMapping("/memory/refresh")
  public java.util.Map<String, Object> refreshMemory() {
    service.refreshMemory();
    return java.util.Map.of("success", true);
  }

  @GetMapping("/patient-memory")
  public ContextDtos.PatientMemoryBrief patientMemory() { return patientMemoryService.getBrief(); }

  @PostMapping("/patient-memory")
  public ContextDtos.PatientMemoryItem savePatientMemory(
      @RequestBody ContextDtos.SavePatientMemoryRequest request) {
    return patientMemoryService.save(request);
  }

  @DeleteMapping("/patient-memory/{id}")
  public java.util.Map<String, Object> retirePatientMemory(@PathVariable long id) {
    patientMemoryService.retire(id);
    return java.util.Map.of("success", true);
  }
}
