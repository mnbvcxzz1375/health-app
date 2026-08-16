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
 * Zepp (Amazfit) Provider（OAuth 2.0 实现）。
 *
 * <p>API 文档: https://docs.zepp.com/zh/docs/work-flow/
 * Zepp（华米/Amazfit）通过 OpenAPI 提供运动健康数据。
 * 端点:
 * - 授权: https://oauth.zepp.com/oauth2/authorize
 * - Token: https://oauth.zepp.com/oauth2/token
 * - 用户信息: /v1/user/profile
 * - 数据: /v1/user/data/{type}
 */
@Component
public class ZeppProvider extends AbstractOAuthProvider {

  private final String clientId;
  private final String clientSecret;
  private final String authUrl;
  private final String tokenUrl;
  private final String apiBase;
  private final OAuthHttpHelper http;

  public ZeppProvider(
      @Value("${device.providers.zepp.client-id:}") String clientId,
      @Value("${device.providers.zepp.client-secret:}") String clientSecret,
      @Value("${device.providers.zepp.auth-url:https://oauth.zepp.com/oauth2/authorize}") String authUrl,
      @Value("${device.providers.zepp.token-url:https://oauth.zepp.com/oauth2/token}") String tokenUrl,
      @Value("${device.providers.zepp.api-base:https://openapi.zepp.com}") String apiBase,
      OAuthHttpHelper http
  ) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authUrl = authUrl;
    this.tokenUrl = tokenUrl;
    this.apiBase = apiBase;
    this.http = http;
  }

  @Override public String providerName() { return "zepp"; }
  @Override public String displayName() { return "Zepp"; }
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
        + "&scope=profile,activity,heartrate,sleep"
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

    // 1) 用户档案（身高 / 体重 / BMI）
    try {
      JsonNode profile = http.bearerGet(apiBase + "/v1/user/profile", accessToken);
      Instant recordedAt = Instant.now();
      UnifiedHealthRecord empty = UnifiedHealthRecord.empty("zepp", "Amazfit Watch", recordedAt);
      Double weight = readDouble(profile, "weight");
      Double height = readDouble(profile, "height");
      Double bmi = (weight != null && height != null && height > 0)
          ? weight / Math.pow(height / 100, 2) : null;
      records.add(new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), recordedAt,
          null, null, null, null, null, null, null, null, null, null,
          weight, height, bmi, null, null, null, null, null, null,
          null, null, null, null, null, null));
    } catch (Exception ignored) { /* skip */ }

    // 2) 心率数据
    try {
      JsonNode hr = http.bearerGet(
          apiBase + "/v1/user/data/heartrate?start_date=" + from + "&end_date=" + to, accessToken);
      JsonNode arr = hr.isArray() ? hr : hr.path("data");
      if (arr.isArray()) {
        for (JsonNode item : arr) {
          Instant recordedAt = parseDate(item.path("date").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("zepp", "Amazfit Watch", recordedAt);
          Integer hrAvg = readInt(item, "avg_hr");
          Integer hrRest = readInt(item, "resting_hr");
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              hrAvg, hrRest, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    // 3) 步数 + 活动数据
    try {
      JsonNode activity = http.bearerGet(
          apiBase + "/v1/user/data/activity?start_date=" + from + "&end_date=" + to, accessToken);
      JsonNode arr = activity.isArray() ? activity : activity.path("data");
      if (arr.isArray()) {
        for (JsonNode item : arr) {
          Instant recordedAt = parseDate(item.path("date").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("zepp", "Amazfit Watch", recordedAt);
          Integer steps = readInt(item, "steps");
          Integer calories = readInt(item, "calories");
          Integer exerciseMin = readInt(item, "exercise_minutes");
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              null, null, null, steps, exerciseMin, null, calories, null, null, null,
              null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    // 4) 睡眠
    try {
      JsonNode sleep = http.bearerGet(
          apiBase + "/v1/user/data/sleep?start_date=" + from + "&end_date=" + to, accessToken);
      JsonNode arr = sleep.isArray() ? sleep : sleep.path("data");
      if (arr.isArray()) {
        for (JsonNode item : arr) {
          Instant recordedAt = parseDate(item.path("date").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("zepp", "Amazfit Watch", recordedAt);
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
    return List.of("heart_rate", "steps", "calories", "sleep_duration",
        "deep_sleep", "rem_sleep", "weight", "bmi");
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

  private static Double readDouble(JsonNode node, String path) {
    JsonNode v = node.path(path);
    if (v.isMissingNode() || v.isNull()) return null;
    if (v.isNumber()) return v.asDouble();
    try { return Double.parseDouble(v.asText()); } catch (Exception e) { return null; }
  }
}
