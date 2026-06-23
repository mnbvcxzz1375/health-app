package com.ahealth.backend.monitor;

import java.util.List;

public final class MonitorDtos {
  private MonitorDtos() {}

  public record MonitorLatestResponse(
      int hr,
      int sleep,
      double deepSleep,
      int awake,
      int stress,
      String updatedAt
  ) {}

  public record MonitorTrendResponse(
      List<String> labels,
      List<Integer> values,
      String insight,
      String suggestion
  ) {}
}
