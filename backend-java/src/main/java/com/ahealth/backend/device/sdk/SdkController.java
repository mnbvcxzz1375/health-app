package com.ahealth.backend.device.sdk;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import com.ahealth.backend.device.model.UnifiedHealthRecord;
import com.ahealth.backend.device.provider.OpenSdkProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
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
 * 开放 SDK Controller。
 *
 * <p>两部分端点：
 * <ul>
 *   <li>/api/devices/sdk/keys/** — 管理 API Key（创建/列出/撤销），走标准用户 Token 鉴权</li>
 *   <li>/api/devices/sdk/reading — 第三方推送数据，走 X-SDK-API-Key 鉴权（由 SdkAuthFilter 处理）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/devices/sdk")
public class SdkController {

  private final SdkKeyService sdkKeyService;
  private final OpenSdkProvider openSdkProvider;
  private final OpenApiSpecGenerator openApiSpecGenerator;

  public SdkController(SdkKeyService sdkKeyService, OpenSdkProvider openSdkProvider, OpenApiSpecGenerator openApiSpecGenerator) {
    this.sdkKeyService = sdkKeyService;
    this.openSdkProvider = openSdkProvider;
    this.openApiSpecGenerator = openApiSpecGenerator;
  }

  // ===== API Key 管理端点（走标准用户 Token 鉴权）=====

  /** 创建新的 SDK API Key（明文仅返回一次）。 */
  @PostMapping("/keys")
  public SdkKeyService.SdkKeyCreation createKey(@RequestBody CreateKeyRequest req) {
    long userId = CurrentUser.requireUserId();
    return sdkKeyService.createKey(userId, req.appName(), req.contactEmail());
  }

  /** 列出当前用户的所有 SDK Key。 */
  @GetMapping("/keys")
  public List<SdkKeyService.SdkKeyItem> listKeys() {
    long userId = CurrentUser.requireUserId();
    return sdkKeyService.listKeys(userId);
  }

  /** 撤销指定 Key。 */
  @DeleteMapping("/keys/{keyId}")
  public Map<String, Object> revokeKey(@PathVariable int keyId) {
    long userId = CurrentUser.requireUserId();
    sdkKeyService.revokeKey(userId, keyId);
    return Map.of("success", true, "message", "Key 已撤销");
  }

  // ===== 第三方推送数据端点（走 X-SDK-API-Key 鉴权）=====

  /**
   * 第三方通过 SDK 推送单条 UnifiedHealthRecord。
   * 由 SdkAuthFilter 完成 API Key 校验并设置 sdkUserId request attribute。
   */
  @PostMapping("/reading")
  public Map<String, Object> pushReading(HttpServletRequest request, @RequestBody SdkReadingRequest req) {
    long userId = SdkAuthFilter.currentSdkUserId(request);
    if (req == null) {
      throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "请求体不能为空");
    }
    UnifiedHealthRecord record = buildRecord(req);
    UnifiedHealthRecord saved = openSdkProvider.pushReading(userId, req.sourceDevice(), record);
    return Map.of(
        "success", true,
        "provider", saved.provider(),
        "sourceDevice", saved.sourceDevice(),
        "recordedAt", saved.recordedAt().toString()
    );
  }

  /**
   * 第三方批量推送数据。
   */
  @PostMapping("/readings/batch")
  public Map<String, Object> pushReadings(HttpServletRequest request, @RequestBody SdkBatchRequest req) {
    long userId = SdkAuthFilter.currentSdkUserId(request);
    if (req == null || req.readings() == null) {
      throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "readings 不能为空");
    }
    int count = 0;
    for (SdkReadingRequest r : req.readings()) {
      try {
        UnifiedHealthRecord record = buildRecord(r);
        openSdkProvider.pushReading(userId, r.sourceDevice(), record);
        count++;
      } catch (Exception ignored) {
        // 单条失败不影响其他
      }
    }
    return Map.of("success", true, "count", count);
  }

  // ===== OpenAPI Schema 端点（公开）=====

  /** 返回 OpenAPI 3.0 JSON Spec。 */
  @GetMapping("/schema")
  public Map<String, Object> getSchema() {
    return openApiSpecGenerator.generateSpec();
  }

  // ===== 构造 UnifiedHealthRecord =====
  private UnifiedHealthRecord buildRecord(SdkReadingRequest req) {
    Instant recordedAt = req.recordedAt() != null && !req.recordedAt().isBlank()
        ? Instant.parse(req.recordedAt()) : Instant.now();
    return new UnifiedHealthRecord(
        "sdk", req.sourceDevice() != null ? req.sourceDevice() : "OpenSDK Device", recordedAt,
        req.heartRateAvgBpm(), req.heartRateRestingBpm(), req.hrvMillis(),
        req.steps(), req.exerciseMinutes(), req.standHours(),
        req.activeEnergyKcal(), req.flightsClimbed(), req.vo2Max(), req.stressScore(),
        req.weightKg(), req.heightCm(), req.bmi(),
        req.systolicBp(), req.diastolicBp(), req.bloodGlucose(),
        req.bodyTemperature(), req.spo2(), req.respiratoryRate(),
        req.sleepDurationHours(), req.deepSleepHours(), req.remSleepHours(),
        req.sleepScore(), req.awakeTimes(), req.mindfulMinutes()
    );
  }

  // ===== 请求 DTO =====
  public record CreateKeyRequest(String appName, String contactEmail) {}

  public record SdkReadingRequest(
      String sourceDevice, String recordedAt,
      Integer heartRateAvgBpm, Integer heartRateRestingBpm, Integer hrvMillis,
      Integer steps, Integer exerciseMinutes, Integer standHours,
      Integer activeEnergyKcal, Integer flightsClimbed, Double vo2Max, Integer stressScore,
      Double weightKg, Double heightCm, Double bmi,
      Integer systolicBp, Integer diastolicBp, Double bloodGlucose,
      Double bodyTemperature, Integer spo2, Integer respiratoryRate,
      Double sleepDurationHours, Double deepSleepHours, Double remSleepHours,
      Integer sleepScore, Integer awakeTimes, Integer mindfulMinutes
  ) {}

  public record SdkBatchRequest(List<SdkReadingRequest> readings) {}
}
