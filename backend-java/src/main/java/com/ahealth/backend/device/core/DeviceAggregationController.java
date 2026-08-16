package com.ahealth.backend.device.core;

import com.ahealth.backend.common.CurrentUser;
import com.ahealth.backend.device.model.DeviceAggregationDtos.AppleHealthSnapshotRequest;
import com.ahealth.backend.device.model.DeviceAggregationDtos.AppleHealthSnapshotResponse;
import com.ahealth.backend.device.model.DeviceAggregationDtos.AuthorizeResponse;
import com.ahealth.backend.device.model.DeviceAggregationDtos.BindingItem;
import com.ahealth.backend.device.model.DeviceAggregationDtos.ManualInputRequest;
import com.ahealth.backend.device.model.DeviceAggregationDtos.ManualInputResponse;
import com.ahealth.backend.device.model.DeviceAggregationDtos.MetricRouteResponse;
import com.ahealth.backend.device.model.DeviceAggregationDtos.OperationResult;
import com.ahealth.backend.device.model.DeviceAggregationDtos.ProviderInfo;
import com.ahealth.backend.device.model.DeviceAggregationDtos.SyncLogItem;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备聚合平台主控制器，接管 {@code /api/devices} 路径。
 *
 * <p>旧的 {@code DeviceController} 已迁移到 {@code /api/devices/legacy}，标记 {@code @Deprecated}。
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceAggregationController {

  private final DeviceAggregationService service;

  public DeviceAggregationController(DeviceAggregationService service) {
    this.service = service;
  }

  // ===== Provider 列表 =====

  @GetMapping("/providers")
  public List<ProviderInfo> listProviders() {
    return service.listProviders();
  }

  // ===== 绑定管理 =====

  @GetMapping("/bindings")
  public List<BindingItem> listBindings() {
    return service.listBindings(CurrentUser.requireUserId());
  }

  @PostMapping("/bindings/{provider}/authorize")
  public AuthorizeResponse authorize(@PathVariable String provider) {
    return service.startOAuth(CurrentUser.requireUserId(), provider);
  }

  /** OAuth 回调（permitAll，由 state 校验用户身份） */
  @GetMapping("/oauth/callback/{provider}")
  public OperationResult oauthCallback(
      @PathVariable String provider,
      @RequestParam("code") String code,
      @RequestParam(value = "state", required = false) String state
  ) {
    return service.handleOAuthCallback(provider, code, state);
  }

  @DeleteMapping("/bindings/{bindingId}")
  public OperationResult deleteBinding(@PathVariable int bindingId) {
    return service.deleteBinding(CurrentUser.requireUserId(), bindingId);
  }

  @PostMapping("/bindings/{bindingId}/sync")
  public OperationResult syncBinding(@PathVariable int bindingId) {
    return service.syncBinding(CurrentUser.requireUserId(), bindingId);
  }

  // ===== 路由 =====

  @GetMapping("/route/{metric}")
  public MetricRouteResponse route(@PathVariable String metric) {
    return service.route(CurrentUser.requireUserId(), metric);
  }

  // ===== 手动输入 =====

  @PostMapping("/manual")
  public ManualInputResponse pushManual(@RequestBody ManualInputRequest req) {
    return service.pushManual(CurrentUser.requireUserId(), req);
  }

  // ===== Apple Health 快照 =====

  @PostMapping("/apple-health/snapshot")
  public AppleHealthSnapshotResponse receiveAppleHealthSnapshot(@RequestBody AppleHealthSnapshotRequest snapshot) {
    return service.receiveAppleHealthSnapshot(CurrentUser.requireUserId(), snapshot);
  }

  // ===== 同步日志 =====

  @GetMapping("/sync-logs/{bindingId}")
  public List<SyncLogItem> listSyncLogs(@PathVariable int bindingId) {
    return service.listSyncLogs(CurrentUser.requireUserId(), bindingId);
  }
}
