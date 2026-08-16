package com.ahealth.backend.device;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @deprecated 已被 {@link com.ahealth.backend.device.core.DeviceAggregationController} 接管 /api/devices 路径。
 *             旧接口保留在 /api/devices/legacy 用于向后兼容，前端应迁移到新接口。
 */
@Deprecated
@RestController
@RequestMapping("/api/devices/legacy")
public class DeviceController {
  private final DeviceService deviceService;

  public DeviceController(DeviceService deviceService) {
    this.deviceService = deviceService;
  }

  @GetMapping
  public List<DeviceDtos.DeviceItem> list() {
    return deviceService.list();
  }

  @PostMapping
  public DeviceDtos.DeviceItem create(@Valid @RequestBody DeviceDtos.CreateDeviceRequest request) {
    return deviceService.create(request);
  }

  @PostMapping("/{id}/sync")
  public DeviceDtos.DeviceItem sync(@PathVariable long id) {
    return deviceService.sync(id);
  }

  @DeleteMapping("/{id}")
  public Map<String, Boolean> delete(@PathVariable long id) {
    deviceService.delete(id);
    return Map.of("success", true);
  }
}
