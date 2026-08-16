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
 * Oura Ring Provider（OAuth 2.0 标准实现）。
 *
 * <p>API 文档: https://cloud.ouraring.com/v2/docs
 * 端点:
 * - 授权: https://cloud.ouraring.com/oauth/authorize
 * - Token: https://api.ouraring.com/v1/oauth/token
 * - 每日活动: /v2/usercollection/daily_activity
 * - 睡眠: /v2/usercollection/sleep
 * - 心率: /v2/usercollection/heartrate
 */
@Component
public class OuraProvider extends AbstractOAuthProvider {

  private final String clientId;
  private final String clientSecret;
  private final String authUrl;
  private final String tokenUrl;
  private final String apiBase;
  private final OAuthHttpHelper http;

  public OuraProvider(
      @Value("${device.providers.oura.client-id:}") String clientId,
      @Value("${device.providers.oura.client-secret:}") String clientSecret,
      @Value("${device.providers.oura.auth-url:https://cloud.ouraring.com/oauth/authorize}") String authUrl,
      @Value("${device.providers.oura.token-url:https://api.ouraring.com/v1/oauth/token}") String tokenUrl,
      @Value("${device.providers.oura.api-base:https://api.ouraring.com/v2}") String apiBase,
      OAuthHttpHelper http
  ) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authUrl = authUrl;
    this.tokenUrl = tokenUrl;
    this.apiBase = apiBase;
    this.http = http;
  }

  @Override public String providerName() { return "oura"; }
  @Override public String displayName() { return "Oura Ring"; }
  @Override public String deviceType() { return "ring"; }
  @Override public boolean isConfigured() {
    return !clientId.isBlank() && !clientSecret.isBlank();
  }

  @Override
  protected String buildAuthorizeUrl(long userId, String redirectUri) {
    return authUrl
        + "?response_type=code"
        + "&client_id=" + OAuthHttpHelper.urlEncode(clientId)
        + "&redirect_uri=" + OAuthHttpHelper.urlEncode(redirectUri)
        + "&scope=personal%20daily"
        + "&state=" + userId + "-" + Long.toHexString(System.currentTimeMillis());
  }

  @Override
  protected OAuthTokenExchange doExchangeCode(String code, String redirectUri) {
    Map<String, String> params = Map.of(
        "code", code,
        "grant_type", "authorization_code",
        "redirect_uri", redirectUri,
        "client_id", clientId,
        "client_secret", clientSecret
    );
    String json = http.formPost(tokenUrl, params, null);
    return http.parseTokenResponse(json, json);
  }

  @Override
  protected OAuthTokenExchange doRefreshToken(String refreshToken) {
    Map<String, String> params = Map.of(
        "refresh_token", refreshToken,
        "grant_type", "refresh_token",
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

    // 1) 每日活动（步数 / 卡路里 / 站立小时 / 锻炼分钟）
    try {
      JsonNode activity = http.bearerGet(
          apiBase + "/usercollection/daily_activity?start_date=" + from + "&end_date=" + to,
          accessToken);
      JsonNode data = activity.path("data");
      if (data.isArray()) {
        for (JsonNode item : data) {
          Instant recordedAt = parseOuraDate(item.path("day").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("oura", "Oura Ring", recordedAt);
          Integer steps = readInt(item, "steps");
          Integer activeKcal = readInt(item, "active_calories");
          Integer exerciseMin = readInt(item, "high_activity_time_minutes");
          Integer standHours = readInt(item, "meet_daily_targets") != null
              && item.path("meet_daily_targets").asInt() == 1 ? 1 : null;
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              null, null, null, steps, exerciseMin, standHours, activeKcal, null, null, null,
              null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null));
        }
      }
    } catch (Exception ignored) { /* 端点失败不影响其他端点 */ }

    // 2) 睡眠（总时长 / 深睡 / REM / HRV / 心率 / 呼吸 / SpO2）
    try {
      JsonNode sleep = http.bearerGet(
          apiBase + "/usercollection/sleep?start_date=" + from + "&end_date=" + to,
          accessToken);
      JsonNode data = sleep.path("data");
      if (data.isArray()) {
        for (JsonNode item : data) {
          Instant recordedAt = parseOuraDate(item.path("day").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("oura", "Oura Ring", recordedAt);
          Double totalHours = readDouble(item, "total_sleep_duration") / 3600.0;
          Double deepHours = readDouble(item, "deep_sleep_duration") / 3600.0;
          Double remHours = readDouble(item, "rem_sleep_duration") / 3600.0;
          Integer hrv = readInt(item, "average_hrv");
          Integer hrAvg = readInt(item, "average_heart_rate");
          Integer rr = readInt(item, "average_breath");
          Integer spo2 = readInt(item, "average_breath") != null ? null : readInt(item, "spo2");
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              hrAvg, null, hrv, null, null, null, null, null, null,
              null, null, null, null, null, null, null, null,
              spo2, rr,
              totalHours, deepHours, remHours, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    return records;
  }

  @Override
  public List<String> supportedMetrics() {
    return List.of("heart_rate", "hrv", "sleep_duration", "deep_sleep", "rem_sleep",
        "steps", "calories", "spo2", "respiratory_rate");
  }

  // ===== 辅助方法 =====
  private static Instant parseOuraDate(String day) {
    if (day == null || day.isBlank()) return null;
    try {
      return LocalDate.parse(day).atStartOfDay().toInstant(OffsetDateTime.now().getOffset());
    } catch (Exception e) {
      return null;
    }
  }

  private static Integer readInt(JsonNode node, String path) {
    JsonNode v = node.path(path);
    if (v.isMissingNode() || v.isNull()) return null;
    if (v.isNumber()) return v.asInt();
    try { return Integer.parseInt(v.asText()); } catch (Exception e) { return null; }
  }

  private static double readDouble(JsonNode node, String path) {
    JsonNode v = node.path(path);
    if (v.isMissingNode() || v.isNull()) return 0.0;
    if (v.isNumber()) return v.asDouble();
    try { return Double.parseDouble(v.asText()); } catch (Exception e) { return 0.0; }
  }
}
