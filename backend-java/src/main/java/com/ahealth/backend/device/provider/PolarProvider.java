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
 * Polar AccessLink Provider（OAuth 2.0 实现）。
 *
 * <p>API 文档: https://www.polar.com/accesslink-api
 * Polar 偏运动训练场景（跑步 / 骑行 / 心率训练）。
 * 端点:
 * - 授权: https://flow.polar.com/oauth2/authorization
 * - Token: https://polarremote.com/v2/oauth2/token
 * - 训练数据: /v3/users/{user-id}/exercise-transactions
 * - 物理信息: /v3/users/{user-id}/physical-information
 * - 每日活动: /v3/users/{user-id}/activity-transactions
 */
@Component
public class PolarProvider extends AbstractOAuthProvider {

  private final String clientId;
  private final String clientSecret;
  private final String authUrl;
  private final String tokenUrl;
  private final String apiBase;
  private final OAuthHttpHelper http;

  public PolarProvider(
      @Value("${device.providers.polar.client-id:}") String clientId,
      @Value("${device.providers.polar.client-secret:}") String clientSecret,
      @Value("${device.providers.polar.auth-url:https://flow.polar.com/oauth2/authorization}") String authUrl,
      @Value("${device.providers.polar.token-url:https://polarremote.com/v2/oauth2/token}") String tokenUrl,
      @Value("${device.providers.polar.api-base:https://www.polaraccesslink.com}") String apiBase,
      OAuthHttpHelper http
  ) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authUrl = authUrl;
    this.tokenUrl = tokenUrl;
    this.apiBase = apiBase;
    this.http = http;
  }

  @Override public String providerName() { return "polar"; }
  @Override public String displayName() { return "Polar"; }
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
        + "&state=" + userId + "-" + Long.toHexString(System.currentTimeMillis());
  }

  @Override
  protected OAuthTokenExchange doExchangeCode(String code, String redirectUri) {
    Map<String, String> params = Map.of(
        "grant_type", "authorization_code",
        "code", code,
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
        "grant_type", "refresh_token",
        "refresh_token", refreshToken
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
    String polarUserId = (bindingExternalId == null || bindingExternalId.isBlank())
        ? String.valueOf(userId) : bindingExternalId;

    // 1) 物理信息：静息心率 / 最大心率 / 体重
    try {
      JsonNode physInfo = http.bearerGet(
          apiBase + "/v3/users/" + polarUserId + "/physical-information", accessToken);
      Instant recordedAt = Instant.now();
      UnifiedHealthRecord empty = UnifiedHealthRecord.empty("polar", "Polar Watch", recordedAt);
      Integer restingHr = readInt(physInfo, "resting-heart-rate");
      Integer maxHr = readInt(physInfo, "maximum-heart-rate");
      Double weight = readDouble(physInfo, "weight");
      Double height = readDouble(physInfo, "height");
      Double bmi = (weight != null && height != null && height > 0)
          ? weight / Math.pow(height / 100, 2) : null;
      records.add(new UnifiedHealthRecord(
          empty.provider(), empty.sourceDevice(), recordedAt,
          maxHr, restingHr, null, null, null, null, null, null, null, null,
          weight, height, bmi, null, null, null, null, null, null,
          null, null, null, null, null, null));
    } catch (Exception ignored) { /* skip */ }

    // 2) 训练数据：心率 / 卡路里 / 距离
    try {
      JsonNode exercise = http.bearerGet(
          apiBase + "/v3/users/" + polarUserId + "/exercise-transactions",
          accessToken);
      JsonNode exercises = exercise.path("exercises");
      if (exercises.isArray()) {
        for (JsonNode item : exercises) {
          String dateStr = item.path("start-time").asText("");
          Instant recordedAt = parsePolarDateTime(dateStr);
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("polar", "Polar Watch", recordedAt);
          Integer hrAvg = readInt(item, "heart-rate.avg");
          Integer hrMax = readInt(item, "heart-rate.max");
          Integer calories = readInt(item, "calories");
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              hrAvg, null, null, null, null, null, calories, null, null, null,
              null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    return records;
  }

  @Override
  public List<String> supportedMetrics() {
    return List.of("heart_rate", "hrv", "weight", "bmi", "calories", "exercise_minutes");
  }

  // ===== 辅助方法 =====
  private static Instant parsePolarDateTime(String dateTime) {
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
