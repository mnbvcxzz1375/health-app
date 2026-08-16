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
 * Strava Provider（OAuth 2.0 实现）。
 *
 * <p>API 文档: https://developers.strava.com/docs/reference/
 * Strava 偏运动训练场景（跑步 / 骑行 / 游泳）。
 * 端点:
 * - 授权: https://www.strava.com/oauth/authorize
 * - Token: https://www.strava.com/oauth/token
 * - 活动: /api/v3/athlete/activities
 * - 运动员: /api/v3/athlete
 */
@Component
public class StravaProvider extends AbstractOAuthProvider {

  private final String clientId;
  private final String clientSecret;
  private final String authUrl;
  private final String tokenUrl;
  private final String apiBase;
  private final OAuthHttpHelper http;

  public StravaProvider(
      @Value("${device.providers.strava.client-id:}") String clientId,
      @Value("${device.providers.strava.client-secret:}") String clientSecret,
      @Value("${device.providers.strava.auth-url:https://www.strava.com/oauth/authorize}") String authUrl,
      @Value("${device.providers.strava.token-url:https://www.strava.com/oauth/token}") String tokenUrl,
      @Value("${device.providers.strava.api-base:https://www.strava.com/api/v3}") String apiBase,
      OAuthHttpHelper http
  ) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authUrl = authUrl;
    this.tokenUrl = tokenUrl;
    this.apiBase = apiBase;
    this.http = http;
  }

  @Override public String providerName() { return "strava"; }
  @Override public String displayName() { return "Strava"; }
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
        + "&approval_prompt=auto"
        + "&scope=read,activity:read,profile:read_all"
        + "&state=" + userId + "-" + Long.toHexString(System.currentTimeMillis());
  }

  @Override
  protected OAuthTokenExchange doExchangeCode(String code, String redirectUri) {
    Map<String, String> params = Map.of(
        "client_id", clientId,
        "client_secret", clientSecret,
        "code", code,
        "grant_type", "authorization_code"
    );
    String json = http.formPost(tokenUrl, params, null);
    return http.parseTokenResponse(json, json);
  }

  @Override
  protected OAuthTokenExchange doRefreshToken(String refreshToken) {
    Map<String, String> params = Map.of(
        "client_id", clientId,
        "client_secret", clientSecret,
        "refresh_token", refreshToken,
        "grant_type", "refresh_token"
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

    // 1) 运动员档案（体重 / VO2Max）
    try {
      JsonNode athlete = http.bearerGet(apiBase + "/athlete", accessToken);
      Instant recordedAt = Instant.now();
      UnifiedHealthRecord empty = UnifiedHealthRecord.empty("strava", "Strava", recordedAt);
      Double weight = readDouble(athlete, "weight");
      records.add(new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), recordedAt,
          null, null, null, null, null, null, null, null, null, null,
          weight, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null));
    } catch (Exception ignored) { /* skip */ }

    // 2) 活动（心率 / 卡路里 / 步数 / 锻炼分钟）
    try {
      long fromEpoch = from.atStartOfDay(OffsetDateTime.now().getOffset()).toEpochSecond();
      long toEpoch = to.plusDays(1).atStartOfDay(OffsetDateTime.now().getOffset()).toEpochSecond();
      JsonNode activities = http.bearerGet(
          apiBase + "/athlete/activities?before=" + toEpoch + "&after=" + fromEpoch + "&per_page=100",
          accessToken);
      if (activities.isArray()) {
        for (JsonNode item : activities) {
          String dateStr = item.path("start_date").asText("");
          Instant recordedAt = parseStravaDateTime(dateStr);
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("strava", "Strava", recordedAt);
          Integer hrAvg = readInt(item, "average_heartrate");
          Integer hrMax = readInt(item, "max_hearctrate");
          Integer calories = readInt(item, "calories");
          Integer movingTime = readInt(item, "moving_time");
          Integer exerciseMin = movingTime != null ? movingTime / 60 : null;
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              hrAvg, null, null, null, exerciseMin, null, calories, null, null, null,
              null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    return records;
  }

  @Override
  public List<String> supportedMetrics() {
    return List.of("heart_rate", "calories", "exercise_minutes", "weight");
  }

  // ===== 辅助方法 =====
  private static Instant parseStravaDateTime(String dateTime) {
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
