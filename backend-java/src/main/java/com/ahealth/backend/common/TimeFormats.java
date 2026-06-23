package com.ahealth.backend.common;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class TimeFormats {
  private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

  private TimeFormats() {}

  public static String toIso(LocalDateTime value) {
    return value == null ? "" : value.atZone(ZONE_ID).toOffsetDateTime().toString();
  }
}
