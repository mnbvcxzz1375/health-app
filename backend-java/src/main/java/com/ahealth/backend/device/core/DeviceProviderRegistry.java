package com.ahealth.backend.device.core;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.device.model.DeviceAggregationDtos.ProviderInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Provider 注册中心。
 * Spring 启动时自动收集所有 DeviceProvider Bean,按 providerName() 索引。
 */
@Component
public class DeviceProviderRegistry {

  private final Map<String, DeviceProvider> providers = new ConcurrentHashMap<>();

  public DeviceProviderRegistry(List<DeviceProvider> providerList) {
    for (DeviceProvider p : providerList) {
      if (providers.put(p.providerName(), p) != null) {
        throw new IllegalStateException("Duplicate DeviceProvider name: " + p.providerName());
      }
    }
  }

  /** 按 provider name 获取(找不到抛 400) */
  public DeviceProvider getProvider(String name) {
    DeviceProvider p = providers.get(name);
    if (p == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "未知的 provider: " + name);
    }
    return p;
  }

  /** 获取所有 Provider */
  public List<DeviceProvider> getAllProviders() {
    return new ArrayList<>(providers.values());
  }

  /** 仅获取已配置凭证的 Provider */
  public List<DeviceProvider> getConfiguredProviders() {
    return providers.values().stream().filter(DeviceProvider::isConfigured).toList();
  }

  /** 按 device_type 过滤 Provider */
  public List<DeviceProvider> getProvidersByDeviceType(String deviceType) {
    return providers.values().stream()
        .filter(p -> p.deviceType().equals(deviceType))
        .toList();
  }

  /** 转为 ProviderInfo DTO 列表(用于 /api/devices/providers 端点) */
  public List<ProviderInfo> toProviderInfoList() {
    return providers.values().stream()
        .map(p -> new ProviderInfo(
            p.providerName(),
            p.displayName(),
            p.deviceType(),
            p.isConfigured(),
            p.isAvailable(),
            p.supportedMetrics()
        ))
        .toList();
  }
}
