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
 * Health Connect Provider（OAuth 2.0 实现）。
 *
 * <p>API 文档: https://developer.android.com/health-and-fitness/guides/health-connect
 * Health Connect 是 Android 14+ 提供的健康数据聚合 SDK，
 * 需要 Android SDK/部署方网关。仓库不提供伪造的公共 OAuth 端点；只有显式注入真实端点和凭证后才启用。
 */
@Component
public class HealthConnectProvider extends AbstractOAuthProvider {

  private final String clientId;
  private final String clientSecret;
  private final String authUrl;
  private final String tokenUrl;
  private final String apiBase;
  private final OAuthHttpHelper http;

  public HealthConnectProvider(
      @Value("${device.providers.health-connect.client-id:}") String clientId,
      @Value("${device.providers.health-connect.client-secret:}") String clientSecret,
      @Value("${device.providers.health-connect.auth-url:}") String authUrl,
      @Value("${device.providers.health-connect.token-url:}") String tokenUrl,
      @Value("${device.providers.health-connect.api-base:}") String apiBase,
      OAuthHttpHelper http
  ) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authUrl = authUrl;
    this.tokenUrl = tokenUrl;
    this.apiBase = apiBase;
    this.http = http;
  }

  @Override public String providerName() { return "health-connect"; }
  @Override public String displayName() { return "Health Connect"; }
  @Override public String deviceType() { return "other"; }
  @Override public boolean isConfigured() {
    return !clientId.isBlank() && !clientSecret.isBlank()
        && !authUrl.isBlank() && !tokenUrl.isBlank() && !apiBase.isBlank();
  }

  @Override
  protected String buildAuthorizeUrl(long userId, String redirectUri) {
    return authUrl
        + "?response_type=code"
        + "&client_id=" + OAuthHttpHelper.urlEncode(clientId)
        + "&redirect_uri=" + OAuthHttpHelper.urlEncode(redirectUri)
        + "&scope=HeartRate,Steps,Sleep,Nutrition,Exercise"
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

    // 1) 步数
    try {
      JsonNode steps = http.bearerGet(
          apiBase + "/v1/users/me/steps?start_date=" + from + "&end_date=" + to, accessToken);
      JsonNode arr = steps.isArray() ? steps : steps.path("data");
      if (arr.isArray()) {
        for (JsonNode item : arr) {
          Instant recordedAt = parseDate(item.path("date").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("health-connect", "Android Device", recordedAt);
          Integer stepCount = readInt(item, "steps");
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              null, null, null, stepCount, null, null, null, null, null, null,
              null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    // 2) 心率
    try {
      JsonNode hr = http.bearerGet(
          apiBase + "/v1/users/me/heart_rate?start_date=" + from + "&end_date=" + to, accessToken);
      JsonNode arr = hr.isArray() ? hr : hr.path("data");
      if (arr.isArray()) {
        for (JsonNode item : arr) {
          Instant recordedAt = parseDate(item.path("date").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("health-connect", "Android Device", recordedAt);
          Integer hrAvg = readInt(item, "avg_bpm");
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              hrAvg, null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    // 3) 睡眠
    try {
      JsonNode sleep = http.bearerGet(
          apiBase + "/v1/users/me/sleep?start_date=" + from + "&end_date=" + to, accessToken);
      JsonNode arr = sleep.isArray() ? sleep : sleep.path("data");
      if (arr.isArray()) {
        for (JsonNode item : arr) {
          Instant recordedAt = parseDate(item.path("date").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("health-connect", "Android Device", recordedAt);
          Integer totalSec = readInt(item, "total_sleep_sec");
          Integer deepSec = readInt(item, "deep_sleep_sec");
          Integer remSec = readInt(item, "rem_sleep_sec");
          Double totalH = totalSec != null ? totalSec / 3600.0 : null;
          Double deepH = deepSec != null ? deepSec / 3600.0 : null;
          Double remH = remSec != null ? remSec / 3600.0 : null;
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              null, null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null, null, null, null,
              totalH, deepH, remH, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    return records;
  }

  @Override
  public List<String> supportedMetrics() {
    return List.of("heart_rate", "steps", "sleep_duration", "deep_sleep", "rem_sleep");
  }

  // ===== 辅助方法 =====
  private static Instant parseDate(String date) {
    if (date == null || date.isBlank()) return null;
    try {
      return LocalDate.parse(date).atStartOfDay().toInstant(OffsetDateTime.now().getOffset());
    } catch (Exception e) {
      try { return OffsetDateTime.parse(date).toInstant(); }
      catch (Exception ex) { return null; }
    }
  }

  private static Integer readInt(JsonNode node, String path) {
    JsonNode v = node.path(path);
    if (v.isMissingNode() || v.isNull()) return null;
    if (v.isNumber()) return v.asInt();
    try { return Integer.parseInt(v.asText()); } catch (Exception e) { return null; }
  }
}
