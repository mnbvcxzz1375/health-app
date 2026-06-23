package com.ahealth.backend.device;

import jakarta.validation.constraints.NotBlank;

public final class DeviceDtos {
  private DeviceDtos() {}

  public record DeviceItem(
      long id,
      String name,
      String brand,
      String model,
      String type,
      boolean connected,
      int battery,
      String lastSyncAt
  ) {}

  public record CreateDeviceRequest(
      String name,
      @NotBlank(message = "设备品牌不能为空") String brand,
      @NotBlank(message = "设备型号不能为空") String model,
      String type
  ) {}
}
