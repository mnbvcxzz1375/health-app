package com.ahealth.backend.device.core;

import com.ahealth.backend.device.model.UnifiedHealthRecord;
import java.time.LocalDate;
import java.util.List;

/**
 * 设备 Provider 统一接口。
 * 14 家 OAuth 厂商（Garmin/Oura/Fitbit/...）+ 特殊 Provider（ManualInput/AppleHealth/Bluetooth/OpenSdk/Rook）全部实现此接口。
 *
 * OAuth 类型 Provider 在 isConfigured()=false 时,getAuthorizeUrl() 抛 503,前端展示「未配置」。
 * 非 OAuth 类型 Provider（ManualInput/AppleHealth/Bluetooth/OpenSdk）的 OAuth 方法抛 UnsupportedOperationException。
 */
public interface DeviceProvider {

  /** Provider 标识:garmin/oura/.../apple_health/bluetooth/manual/sdk/rook */
  String providerName();

  /** 显示名称:Garmin/Oura Ring/Apple Health/手动输入/... */
  String displayName();

  /** 设备类型:watch/scale/bp_monitor/cgm/sleep_monitor/pulse_ox/thermometer/rehab_sensor/ring/other */
  String deviceType();

  /** 是否已配置凭证(对 OAuth 类型,检查 client_id/secret 是否非空) */
  boolean isConfigured();

  /**
   * 是否可用于新增绑定。
   * 默认实现 = isConfigured(),非 OAuth 类型(ManualInput/AppleHealth/Bluetooth/OpenSdk)覆写为永远 true。
   */
  default boolean isAvailable() {
    return isConfigured();
  }

  /**
   * 启动 OAuth 授权,返回厂商授权 URL。
   * 非 OAuth Provider 抛 UnsupportedOperationException。
   */
  String getAuthorizeUrl(long userId, String redirectUri);

  /**
   * 用授权码换取 token。
   * 非 OAuth Provider 抛 UnsupportedOperationException。
   */
  OAuthTokenExchange exchangeCode(String code, String redirectUri);

  /**
   * 用 refresh_token 刷新 access_token。
   * 非 OAuth Provider 抛 UnsupportedOperationException。
   */
  OAuthTokenExchange refreshToken(String refreshToken);

  /**
   * 拉取数据。
   * OAuth Provider: 调厂商 API。
   * 非 OAuth Provider(ManualInput/AppleHealth/Bluetooth/OpenSdk): 不主动 pull,返回空列表,数据通过 pushData/receiveSnapshot/pushReading 写入。
   */
  List<UnifiedHealthRecord> pullData(long userId, String bindingExternalId,
                                     OAuthTokenExchange token, LocalDate from, LocalDate to);

  /** 该 Provider 支持的 metric 列表(如 ["heart_rate","steps","sleep_duration"]) */
  List<String> supportedMetrics();
}
