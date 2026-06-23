package com.ahealth.backend.scoring;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/health")
public class HealthScoreController {
  private final HealthScoringService service;
  public HealthScoreController(HealthScoringService service) { this.service = service; }

  @GetMapping("/score")
  public ScoringDtos.HealthScoreResponse score() { return service.getScore(); }
}
