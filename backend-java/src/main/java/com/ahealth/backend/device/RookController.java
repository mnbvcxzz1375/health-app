package com.ahealth.backend.device;

import com.ahealth.backend.common.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/device/rook")
public class RookController {
  private final RookService rookService;

  public RookController(RookService rookService) {
    this.rookService = rookService;
  }

  /** Get authorization URL for connecting a data source */
  @GetMapping("/authorize/{dataSource}")
  public RookDtos.DataSourceAuth authorize(@PathVariable String dataSource) {
    String userId = String.valueOf(CurrentUser.requireUserId());
    return rookService.getDataSourceAuthorizer(userId, dataSource);
  }

  /** Get all authorized data sources */
  @GetMapping("/sources")
  public RookDtos.AuthorizedSources getSources() {
    String userId = String.valueOf(CurrentUser.requireUserId());
    return rookService.getAuthorizedSources(userId);
  }

  /** Get physical health summary for a date */
  @GetMapping("/physical/{date}")
  public RookDtos.PhysicalHealthSummary getPhysical(@PathVariable String date) {
    String userId = String.valueOf(CurrentUser.requireUserId());
    return rookService.getPhysicalHealthSummary(userId, date);
  }

  /** Get sleep health summary for a date */
  @GetMapping("/sleep/{date}")
  public RookDtos.SleepHealthSummary getSleep(@PathVariable String date) {
    String userId = String.valueOf(CurrentUser.requireUserId());
    return rookService.getSleepHealthSummary(userId, date);
  }

  /** Get activity events for a date */
  @GetMapping("/activities/{date}")
  public List<RookDtos.ActivityEvent> getActivities(@PathVariable String date) {
    String userId = String.valueOf(CurrentUser.requireUserId());
    return rookService.getActivityEvents(userId, date);
  }

  /** Check if ROOK is configured */
  @GetMapping("/status")
  public Map<String, Object> status() {
    return Map.of("configured", rookService.isConfigured());
  }
}
