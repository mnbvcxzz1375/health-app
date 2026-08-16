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
 * Fitbit Provider（OAuth 2.0 实现）。
 *
 * <p>API 文档: https://dev.fitbit.com/build/reference/web-api/
 * Fitbit 使用 Basic Auth + client_secret 模式（与 Garmin 相同）。
 * 端点:
 * - 授权: https://www.fitbit.com/oauth2/authorize
 * - Token: https://api.fitbit.com/oauth2/token
 * - 心率: /1/user/-/activities/heart/date/{from}/{to}.json
 * - 活动: /1/user/-/activities/date/{date}.json
 * - 睡眠: /1.2/user/-/sleep/date/{from}/{to}.json
 */
@Component
public class FitbitProvider extends AbstractOAuthProvider {

  private final String clientId;
  private final String clientSecret;
  private final String authUrl;
  private final String tokenUrl;
  private final String apiBase;
  private final OAuthHttpHelper http;

  public FitbitProvider(
      @Value("${device.providers.fitbit.client-id:}") String clientId,
      @Value("${device.providers.fitbit.client-secret:}") String clientSecret,
      @Value("${device.providers.fitbit.auth-url:https://www.fitbit.com/oauth2/authorize}") String authUrl,
      @Value("${device.providers.fitbit.token-url:https://api.fitbit.com/oauth2/token}") String tokenUrl,
      @Value("${device.providers.fitbit.api-base:https://api.fitbit.com}") String apiBase,
      OAuthHttpHelper http
  ) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authUrl = authUrl;
    this.tokenUrl = tokenUrl;
    this.apiBase = apiBase;
    this.http = http;
  }

  @Override public String providerName() { return "fitbit"; }
  @Override public String displayName() { return "Fitbit"; }
  @Override public String deviceType() { return "watch"; }
  @Override public boolean isConfigured() {
    return !clientId.isBlank() && !clientSecret.isBlank();
  }

  @Override
  protected String buildAuthorizeUrl(long userId, String redirectUri) {
    return authUrl
        + "?response_type=code"
        + "&client_id=" + OAuthHttpHelper.urlEncode(clientId)
        + "&redirect_uri=" + OAuthHttpHelper.urlEncode(redirectUri)
        + "&scope=heartrate%20activity%20sleep%20profile%20weight"
        + "&state=" + userId + "-" + Long.toHexString(System.currentTimeMillis());
  }

  @Override
  protected OAuthTokenExchange doExchangeCode(String code, String redirectUri) {
    Map<String, String> params = Map.of(
        "code", code,
        "grant_type", "authorization_code",
        "redirect_uri", redirectUri,
        "client_id", clientId
    );
    Map<String, String> headers = Map.of(
        "Authorization", http.basicAuthHeader(clientId, clientSecret),
        "Content-Type", "application/x-www-form-urlencoded"
    );
    String json = http.formPost(tokenUrl, params, headers);
    return http.parseTokenResponse(json, json);
  }

  @Override
  protected OAuthTokenExchange doRefreshToken(String refreshToken) {
    Map<String, String> params = Map.of(
        "refresh_token", refreshToken,
        "grant_type", "refresh_token"
    );
    Map<String, String> headers = Map.of(
        "Authorization", http.basicAuthHeader(clientId, clientSecret)
    );
    String json = http.formPost(tokenUrl, params, headers);
    return http.parseTokenResponse(json, json);
  }

  @Override
  protected List<UnifiedHealthRecord> doPullData(
      long userId, String bindingExternalId, OAuthTokenExchange token, LocalDate from, LocalDate to
  ) {
    List<UnifiedHealthRecord> records = new ArrayList<>();
    String accessToken = token.accessToken();

    // 1) 每日活动
    try {
      JsonNode activities = http.bearerGet(
          apiBase + "/1/user/-/activities/date/" + from + ".json", accessToken);
      JsonNode summary = activities.path("summary");
      if (!summary.isMissingNode()) {
        UnifiedHealthRecord empty = UnifiedHealthRecord.empty("fitbit", "Fitbit Tracker", Instant.now());
        Integer steps = readInt(summary, "steps");
        Integer floors = readInt(summary, "floors");
        Integer exerciseMin = readInt(summary, "fairlyActiveMinutes");
        Integer activeKcal = readInt(summary, "activityCalories");
        records.add(new UnifiedHealthRecord(
            empty.provider(), empty.sourceDevice(), Instant.now(),
            null, null, null, steps, exerciseMin, null, activeKcal, floors, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null));
      }
    } catch (Exception ignored) { /* skip */ }

    // 2) 睡眠
    try {
      JsonNode sleep = http.bearerGet(
          apiBase + "/1.2/user/-/sleep/date/" + from + "/" + to + ".json", accessToken);
      JsonNode sleepArr = sleep.path("sleep");
      if (sleepArr.isArray()) {
        for (JsonNode item : sleepArr) {
          String dateStr = item.path("dateOfSleep").asText("");
          Instant recordedAt = parseFitbitDate(dateStr);
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("fitbit", "Fitbit Tracker", recordedAt);
          Integer durationMs = readInt(item, "duration");
          Integer deepMs = readInt(item.path("levels").path("summary"), "deep");
          Integer remMs = readInt(item.path("levels").path("summary"), "rem");
          Integer awakeCount = readInt(item, "awakeCount");
          Integer efficiency = readInt(item, "efficiency");
          Double totalH = durationMs != null ? durationMs / 3_600_000.0 : null;
          Double deepH = deepMs != null ? deepMs / 3_600_000.0 : null;
          Double remH = remMs != null ? remMs / 3_600_000.0 : null;
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              null, null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null, null, null, null,
              totalH, deepH, remH, efficiency, awakeCount, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    return records;
  }

  @Override
  public List<String> supportedMetrics() {
    return List.of("heart_rate", "steps", "calories", "sleep_duration",
        "deep_sleep", "rem_sleep", "flights_climbed", "exercise_minutes");
  }

  // ===== 辅助方法 =====
  private static Instant parseFitbitDate(String date) {
    if (date == null || date.isBlank()) return null;
    try {
      return LocalDate.parse(date).atStartOfDay().toInstant(OffsetDateTime.now().getOffset());
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
}
