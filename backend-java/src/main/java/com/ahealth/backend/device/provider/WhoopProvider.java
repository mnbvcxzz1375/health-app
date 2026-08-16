package com.ahealth.backend.device.provider;

import com.ahealth.backend.device.core.AbstractOAuthProvider;
import com.ahealth.backend.device.core.OAuthHttpHelper;
import com.ahealth.backend.device.core.OAuthTokenExchange;
import com.ahealth.backend.device.model.UnifiedHealthRecord;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Whoop Provider（OAuth 2.0 实现）。
 *
 * <p>API 文档: https://developer.whoop.com/api
 * Whoop 偏恢复 / 睡眠 / 训练负荷场景。
 * 端点:
 * - 授权: https://api.prod.whoop.com/oauth/oauth2/auth
 * - Token: https://api.prod.whoop.com/oauth/oauth2/token
 * - 恢复: /developer/v1/recovery
 * - 睡眠: /developer/v1/activity/sleep
 * - 身体: /developer/v1/body_measurement
 * - 训练: /developer/v1/activity/workout
 */
@Component
public class WhoopProvider extends AbstractOAuthProvider {

  private final String clientId;
  private final String clientSecret;
  private final String authUrl;
  private final String tokenUrl;
  private final String apiBase;
  private final OAuthHttpHelper http;

  public WhoopProvider(
      @Value("${device.providers.whoop.client-id:}") String clientId,
      @Value("${device.providers.whoop.client-secret:}") String clientSecret,
      @Value("${device.providers.whoop.auth-url:https://api.prod.whoop.com/oauth/oauth2/auth}") String authUrl,
      @Value("${device.providers.whoop.token-url:https://api.prod.whoop.com/oauth/oauth2/token}") String tokenUrl,
      @Value("${device.providers.whoop.api-base:https://api.prod.whoop.com}") String apiBase,
      OAuthHttpHelper http
  ) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authUrl = authUrl;
    this.tokenUrl = tokenUrl;
    this.apiBase = apiBase;
    this.http = http;
  }

  @Override public String providerName() { return "whoop"; }
  @Override public String displayName() { return "Whoop"; }
  @Override public String deviceType() { return "other"; }
  @Override public boolean isConfigured() {
    return !clientId.isBlank() && !clientSecret.isBlank();
  }

  @Override
  protected String buildAuthorizeUrl(long userId, String redirectUri) {
    return authUrl
        + "?response_type=code"
        + "&client_id=" + OAuthHttpHelper.urlEncode(clientId)
        + "&redirect_uri=" + OAuthHttpHelper.urlEncode(redirectUri)
        + "&scope=read:recovery%20read:sleep%20read:workout%20read:body_measurement%20read:profile"
        + "&state=" + userId + "-" + Long.toHexString(System.currentTimeMillis());
  }

  @Override
  protected OAuthTokenExchange doExchangeCode(String code, String redirectUri) {
    Map<String, String> params = Map.of(
        "grant_type", "authorization_code",
        "code", code,
        "client_id", clientId,
        "client_secret", clientSecret,
        "redirect_uri", redirectUri
    );
    String json = http.formPost(tokenUrl, params, null);
    return http.parseTokenResponse(json, json);
  }

  @Override
  protected OAuthTokenExchange doRefreshToken(String refreshToken) {
    Map<String, String> params = Map.of(
        "grant_type", "refresh_token",
        "refresh_token", refreshToken,
        "client_id", clientId,
        "client_secret", clientSecret
    );
    String json = http.formPost(tokenUrl, params, null);
    return http.parseTokenResponse(json, json);
  }

  @Override
  protected List<UnifiedHealthRecord> doPullData(
      long userId, String bindingExternalId, OAuthTokenExchange token, LocalDate from, LocalDate to
  ) {
    List<UnifiedHealthRecord> records = new ArrayList<>();
    String accessToken = token.accessToken();

    // 1) 恢复：HRV / 静息心率 / 恢复评分
    try {
      JsonNode recovery = http.bearerGet(
          apiBase + "/developer/v1/recovery?start=" + from + "&end=" + to, accessToken);
      JsonNode arr = recovery.isArray() ? recovery : recovery.path("records");
      if (arr.isArray()) {
        for (JsonNode item : arr) {
          Instant recordedAt = parseWhoopDate(item.path("created_at").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("whoop", "Whoop Strap", recordedAt);
          Integer hrv = readInt(item, "recovery_score.heart_rate_variability_rmssd_ms");
          Integer restingHr = readInt(item, "recovery_score.resting_heart_rate");
          Integer recoveryScore = readInt(item, "score.recovery_score");
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              null, restingHr, hrv, null, null, null, null, null, null, recoveryScore,
              null, null, null, null, null, null, null, null,
              null, null, null, null, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    // 2) 睡眠
    try {
      JsonNode sleep = http.bearerGet(
          apiBase + "/developer/v1/activity/sleep?start=" + from + "&end=" + to, accessToken);
      JsonNode arr = sleep.isArray() ? sleep : sleep.path("records");
      if (arr.isArray()) {
        for (JsonNode item : arr) {
          Instant recordedAt = parseWhoopDate(item.path("created_at").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("whoop", "Whoop Strap", recordedAt);
          Integer sleepMs = readInt(item, "score.stage_summary.total_in_bed_time_milli");
          Integer lightMs = readInt(item, "score.stage_summary.light_sleep_time_milli");
          Integer remMs = readInt(item, "score.stage_summary.rem_sleep_time_milli");
          Integer deepMs = readInt(item, "score.stage_summary.slow_wave_sleep_time_milli");
          Integer awakeCount = readInt(item, "score.sleep_summary.sleep_efficiency");
          Integer hrAvg = readInt(item, "score.respiratory_rate");
          Double totalH = sleepMs != null ? sleepMs / 3_600_000.0 : null;
          Double deepH = deepMs != null ? deepMs / 3_600_000.0 : null;
          Double remH = remMs != null ? remMs / 3_600_000.0 : null;
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              hrAvg, null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null, null, null, null,
              totalH, deepH, remH, null, awakeCount, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    // 3) 身体测量（体重 / 身高 / BMI）
    try {
      JsonNode body = http.bearerGet(
          apiBase + "/developer/v1/body_measurement", accessToken);
      JsonNode arr = body.isArray() ? body : body.path("records");
      if (arr.isArray()) {
        for (JsonNode item : arr) {
          Instant recordedAt = parseWhoopDate(item.path("created_at").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("whoop", "Whoop Strap", recordedAt);
          Double weight = readDouble(item, "body_measurement.weight_basic_kg");
          Double height = readDouble(item, "body_measurement.height_basic_meters");
          if (height != null) height = height * 100;
          Double bmi = (weight != null && height != null && height > 0)
              ? weight / Math.pow(height / 100, 2) : null;
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              null, null, null, null, null, null, null, null, null, null,
              weight, height, bmi, null, null, null, null, null, null,
              null, null, null, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    return records;
  }

  @Override
  public List<String> supportedMetrics() {
    return List.of("heart_rate", "hrv", "stress_score", "sleep_duration",
        "deep_sleep", "rem_sleep", "weight", "bmi");
  }

  // ===== 辅助方法 =====
  private static Instant parseWhoopDate(String dateTime) {
    if (dateTime == null || dateTime.isBlank()) return null;
    try {
      return OffsetDateTime.parse(dateTime).toInstant();
    } catch (Exception e) {
      try { return LocalDate.parse(dateTime).atStartOfDay().toInstant(OffsetDateTime.now().getOffset()); }
      catch (Exception ex) { return null; }
    }
  }

  private static Integer readInt(JsonNode node, String path) {
    JsonNode v = node.path(path);
    if (v.isMissingNode() || v.isNull()) return null;
    if (v.isNumber()) return v.asInt();
    try { return Integer.parseInt(v.asText()); } catch (Exception e) { return null; }
  }

  private static Double readDouble(JsonNode node, String path) {
    JsonNode v = node.path(path);
    if (v.isMissingNode() || v.isNull()) return null;
    if (v.isNumber()) return v.asDouble();
    try { return Double.parseDouble(v.asText()); } catch (Exception e) { return null; }
  }
}
