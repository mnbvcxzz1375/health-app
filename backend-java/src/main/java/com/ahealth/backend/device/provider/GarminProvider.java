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
 * Garmin Health API Provider（OAuth 2.0 实现）。
 *
 * <p>API 文档: https://developer.garmin.com/health-api/overview/
 * Garmin 使用 PKCE + client_secret 模式,需要先注册 client。
 * 端点:
 * - 授权: https://connect.garmin.com/oauth2/authorize
 * - Token: https://connectapi.garmin.com/oauth2/token
 * - 每日汇总: /wellness/v2/dailySummaryChart
 * - 健康: /wellness/v1/healthSummary
 * - 睡眠: /wellness/v2/sleep
 */
@Component
public class GarminProvider extends AbstractOAuthProvider {

  private final String clientId;
  private final String clientSecret;
  private final String authUrl;
  private final String tokenUrl;
  private final String apiBase;
  private final OAuthHttpHelper http;

  public GarminProvider(
      @Value("${device.providers.garmin.client-id:}") String clientId,
      @Value("${device.providers.garmin.client-secret:}") String clientSecret,
      @Value("${device.providers.garmin.auth-url:https://connect.garmin.com/oauth2/authorize}") String authUrl,
      @Value("${device.providers.garmin.token-url:https://connectapi.garmin.com/oauth2/token}") String tokenUrl,
      @Value("${device.providers.garmin.api-base:https://connectapi.garmin.com}") String apiBase,
      OAuthHttpHelper http
  ) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authUrl = authUrl;
    this.tokenUrl = tokenUrl;
    this.apiBase = apiBase;
    this.http = http;
  }

  @Override public String providerName() { return "garmin"; }
  @Override public String displayName() { return "Garmin Connect"; }
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
        + "&scope=garmin%3Ahealth%3Aread%20garmin%3Afitness%3Aread"
        + "&state=" + userId + "-" + Long.toHexString(System.currentTimeMillis());
  }

  @Override
  protected OAuthTokenExchange doExchangeCode(String code, String redirectUri) {
    // Garmin 使用 Basic Auth header
    Map<String, String> params = Map.of(
        "code", code,
        "grant_type", "authorization_code",
        "redirect_uri", redirectUri
    );
    Map<String, String> headers = Map.of(
        "Authorization", http.basicAuthHeader(clientId, clientSecret)
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
    String dateParam = "?uploadStartTime=" + from + "T00:00:00&uploadEndTime=" + to + "T23:59:59";

    // 1) 每日汇总：步数 / 心率 / 压力 / 卡路里 / 爬楼
    try {
      JsonNode summaries = http.bearerGet(
          apiBase + "/wellness/v2/dailySummaryChart" + dateParam, accessToken);
      JsonNode array = summaries.isArray() ? summaries : summaries.path("dailySummaries");
      if (array.isArray()) {
        for (JsonNode item : array) {
          Instant recordedAt = parseGarminDate(item.path("calendarDate").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("garmin", "Garmin Watch", recordedAt);
          Integer steps = readInt(item, "totalSteps");
          Integer hrRest = readInt(item, "restingHeartRate");
          Integer hrAvg = readInt(item, "averageHeartRate");
          Integer stressAvg = readInt(item, "averageStressLevel");
          Integer activeKcal = readInt(item, "activeKilocalories");
          Integer floors = readInt(item, "floorsClimbed");
          Integer exerciseMin = readInt(item, "moderateIntensityMinutes");
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              hrAvg, hrRest, null, steps, exerciseMin, null, activeKcal, floors, null, stressAvg,
              null, null, null, null, null, null, null, null,
              null, null, null, null, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    // 2) 睡眠：总时长 / 深睡 / REM
    try {
      JsonNode sleep = http.bearerGet(apiBase + "/wellness/v2/sleep" + dateParam, accessToken);
      JsonNode array = sleep.isArray() ? sleep : sleep.path("dailySleepDTO");
      if (array.isArray()) {
        for (JsonNode item : array) {
          Instant recordedAt = parseGarminDate(item.path("calendarDate").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("garmin", "Garmin Watch", recordedAt);
          Integer sleepMs = readInt(item, "sleepTimeSeconds");
          Integer deepMs = readInt(item, "deepSleepSeconds");
          Integer remMs = readInt(item, "remSleepSeconds");
          Integer awakeCount = readInt(item, "awakeningCount");
          Double totalH = sleepMs != null ? sleepMs / 3600.0 : null;
          Double deepH = deepMs != null ? deepMs / 3600.0 : null;
          Double remH = remMs != null ? remMs / 3600.0 : null;
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              null, null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null, null, null, null,
              totalH, deepH, remH, null, awakeCount, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    return records;
  }

  @Override
  public List<String> supportedMetrics() {
    return List.of("heart_rate", "hrv", "steps", "calories", "stress_score",
        "sleep_duration", "deep_sleep", "rem_sleep", "flights_climbed");
  }

  // ===== 辅助方法 =====
  private static Instant parseGarminDate(String date) {
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
