package com.ahealth.backend.device.provider;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.device.core.DeviceProvider;
import com.ahealth.backend.device.core.OAuthTokenExchange;
import com.ahealth.backend.device.core.UnifiedRecordWriter;
import com.ahealth.backend.device.model.UnifiedHealthRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 蓝牙设备 Provider（特殊 Provider，非 OAuth）。
 *
 * <p>封装前端 Web Bluetooth API 读取的 BLE 心率/血氧/体温等读数。
 * 不主动 pullData，仅通过 {@link #pushReading(long, String, Map, Instant)} 接收前端推送。
 */
@Component
public class BluetoothProvider implements DeviceProvider {

  private final UnifiedRecordWriter writer;

  public BluetoothProvider(UnifiedRecordWriter writer) {
    this.writer = writer;
  }

  @Override
  public String providerName() { return "bluetooth"; }

  @Override
  public String displayName() { return "蓝牙设备"; }

  @Override
  public String deviceType() { return "watch"; }

  @Override
  public boolean isConfigured() { return true; }

  @Override
  public boolean isAvailable() { return true; }

  @Override
  public List<String> supportedMetrics() {
    return List.of("heart_rate", "spo2", "body_temperature", "blood_pressure", "blood_glucose");
  }

  // ===== 非 OAuth 方法：抛 UnsupportedOperationException =====
  @Override
  public String getAuthorizeUrl(long userId, String redirectUri) {
    throw new UnsupportedOperationException("BluetoothProvider 不支持 OAuth");
  }

  @Override
  public OAuthTokenExchange exchangeCode(String code, String redirectUri) {
    throw new UnsupportedOperationException("BluetoothProvider 不支持 OAuth");
  }

  @Override
  public OAuthTokenExchange refreshToken(String refreshToken) {
    throw new UnsupportedOperationException("BluetoothProvider 不支持 OAuth");
  }

  @Override
  public List<UnifiedHealthRecord> pullData(
      long userId, String bindingExternalId, OAuthTokenExchange token, LocalDate from, LocalDate to
  ) {
    return List.of(); // 不主动 pull
  }

  // ===== 核心方法：接收前端 BLE 读数 =====

  /**
   * 接收前端 Web Bluetooth 读数。
   *
   * @param userId     用户 ID
   * @param deviceName 蓝牙设备名（如 "Mi Band 8"）
   * @param reading    读数键值对，支持的 key：
   *                   <ul>
   *                     <li>{@code heart_rate} (Integer bpm)</li>
   *                     <li>{@code spo2} (Integer %)</li>
   *                     <li>{@code body_temperature} (Double ℃)</li>
   *                     <li>{@code systolic_bp} / {@code diastolic_bp} (Integer mmHg)</li>
   *                     <li>{@code blood_glucose} (Double mmol/L)</li>
   *                   </ul>
   * @param recordedAt 采集时间，null 则用当前时间
   */
  public void pushReading(long userId, String deviceName, Map<String, Object> reading, Instant recordedAt) {
    if (reading == null || reading.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "reading 不能为空");
    }
    Instant at = recordedAt != null ? recordedAt : Instant.now();
    String source = deviceName == null || deviceName.isBlank() ? "BLE Device" : deviceName;

    Integer hr = asInt(reading.get("heart_rate"));
    Integer spo2 = asInt(reading.get("spo2"));
    Double bodyTemp = asDouble(reading.get("body_temperature"));
    Integer systolic = asInt(reading.get("systolic_bp"));
    Integer diastolic = asInt(reading.get("diastolic_bp"));
    Double bloodGlucose = asDouble(reading.get("blood_glucose"));

    UnifiedHealthRecord record = new UnifiedHealthRecord(
        "bluetooth", source, at,
        hr, null, null, null, null, null, null, null, null, null,
        null, null, null, systolic, diastolic, bloodGlucose, bodyTemp, spo2, null,
        null, null, null, null, null, null
    );
    writer.writeRecord(userId, record);
  }

  private static Integer asInt(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.intValue();
    try { return Integer.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
  }

  private static Double asDouble(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.doubleValue();
    try { return Double.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
  }
}
