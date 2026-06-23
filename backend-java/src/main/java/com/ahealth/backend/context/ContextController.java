package com.ahealth.backend.context;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/context")
public class ContextController {
  private final ContextService service;
  public ContextController(ContextService service) { this.service = service; }

  @GetMapping("/snapshot")
  public ContextDtos.ContextSnapshot snapshot() { return service.getSnapshot(); }

  @PostMapping("/memory/save")
  public void saveMemory(@RequestBody ContextDtos.SaveMemoryRequest req) { service.saveMemory(req); }

  @PostMapping("/memory/refresh")
  public java.util.Map<String, Object> refreshMemory() {
    service.refreshMemory();
    return java.util.Map.of("success", true);
  }
}
