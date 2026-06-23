package com.ahealth.backend.monitor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {
  private final MonitorService monitorService;

  public MonitorController(MonitorService monitorService) {
    this.monitorService = monitorService;
  }

  @GetMapping("/latest")
  public MonitorDtos.MonitorLatestResponse latest() {
    return monitorService.getLatest();
  }

  @GetMapping("/trends")
  public MonitorDtos.MonitorTrendResponse trend(
      @RequestParam String metric,
      @RequestParam String range
  ) {
    return monitorService.getTrend(metric, range);
  }
}
