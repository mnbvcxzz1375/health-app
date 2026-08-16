package com.ahealth.backend.device.provider;

import com.ahealth.backend.device.core.AbstractOAuthProvider;
import com.ahealth.backend.device.core.OAuthHttpHelper;
import com.ahealth.backend.device.core.OAuthTokenExchange;
import com.ahealth.backend.device.model.UnifiedHealthRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Withings Provider（OAuth 2.0 实现）。
 *
 * <p>API 文档: https://developer.withings.com/api-reference
 * Withings 主要覆盖 body 指标（体重 / BMI / 血压 / 体温 / 血氧）。
 * 端点:
 * - 授权: https://account.withings.com/oauth2_user/authorize2
 * - Token: https://wbsapi.withings.net/v2/oauth2 (action=requesttoken)
 * - 数据: /measure?action=getmeasures
 */
@Component
public class WithingsProvider extends AbstractOAuthProvider {

  private final String clientId;
  private final String clientSecret;
  private final String authUrl;
  private final String tokenUrl;
  private final String apiBase;
  private final OAuthHttpHelper http;
  private final ObjectMapper objectMapper;

  public WithingsProvider(
      @Value("${device.providers.withings.client-id:}") String clientId,
      @Value("${device.providers.withings.client-secret:}") String clientSecret,
      @Value("${device.providers.withings.auth-url:https://account.withings.com/oauth2_user/authorize2}") String authUrl,
      @Value("${device.providers.withings.token-url:https://wbsapi.withings.net/v2/oauth2}") String tokenUrl,
      @Value("${device.providers.withings.api-base:https://wbsapi.withings.net}") String apiBase,
      OAuthHttpHelper http,
      ObjectMapper objectMapper
  ) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authUrl = authUrl;
    this.tokenUrl = tokenUrl;
    this.apiBase = apiBase;
    this.http = http;
    this.objectMapper = objectMapper;
  }

  @Override public String providerName() { return "withings"; }
  @Override public String displayName() { return "Withings"; }
  @Override public String deviceType() { return "scale"; }
  @Override public boolean isConfigured() {
    return !clientId.isBlank() && !clientSecret.isBlank();
  }

  @Override
  protected String buildAuthorizeUrl(long userId, String redirectUri) {
    return authUrl
        + "?response_type=code"
        + "&client_id=" + OAuthHttpHelper.urlEncode(clientId)
        + "&redirect_uri=" + OAuthHttpHelper.urlEncode(redirectUri)
        + "&scope=user.info,user.metrics,user.activity"
        + "&state=" + userId + "-" + Long.toHexString(System.currentTimeMillis());
  }

  @Override
  protected OAuthTokenExchange doExchangeCode(String code, String redirectUri) {
    // Withings token 端点: POST /v2/oauth2?action=requesttoken
    Map<String, String> params = Map.of(
        "action", "requesttoken",
        "grant_type", "authorization_code",
        "client_id", clientId,
        "client_secret", clientSecret,
        "code", code,
        "redirect_uri", redirectUri
    );
    String json = http.formPost(tokenUrl, params, null);
    // Withings 把 token 放在 body.token 字段，提取后再次解析
    return parseWithingsToken(json);
  }

  @Override
  protected OAuthTokenExchange doRefreshToken(String refreshToken) {
    Map<String, String> params = Map.of(
        "action", "requesttoken",
        "grant_type", "refresh_token",
        "client_id", clientId,
        "client_secret", clientSecret,
        "refresh_token", refreshToken
    );
    String json = http.formPost(tokenUrl, params, null);
    return parseWithingsToken(json);
  }

  @Override
  protected List<UnifiedHealthRecord> doPullData(
      long userId, String bindingExternalId, OAuthTokenExchange token, LocalDate from, LocalDate to
  ) {
    List<UnifiedHealthRecord> records = new ArrayList<>();
    String accessToken = token.accessToken();

    // Withings measure API: /measure?action=getmeasures&meastype=...
    // 1: weight(kg), 4: height(m), 6: fat_free_mass, 8: fat_ratio, 9: diastolic_bp, 10: systolic_bp,
    // 11: heart_rate, 54: spo2, 71: body_temp
    try {
      long fromDateEpoch = from.atStartOfDay(OffsetDateTime.now().getOffset()).toEpochSecond();
      long toDateEpoch = to.plusDays(1).atStartOfDay(OffsetDateTime.now().getOffset()).toEpochSecond();
      JsonNode resp = http.bearerGet(
          apiBase + "/measure?action=getmeasures&meastype=1,4,6,8,9,10,11,54,71"
              + "&startdate=" + fromDateEpoch + "&enddate=" + toDateEpoch,
          accessToken);
      JsonNode measureGroups = resp.path("body").path("measuregrps");
      if (measureGroups.isArray()) {
        for (JsonNode grp : measureGroups) {
          Instant recordedAt = Instant.ofEpochSecond(grp.path("date").asLong());
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("withings", "Withings Scale", recordedAt);
          Double weight = null, height = null, temp = null;
          Integer systolic = null, diastolic = null, hr = null, spo2 = null;
          JsonNode measures = grp.path("measures");
          if (measures.isArray()) {
            for (JsonNode m : measures) {
              int type = m.path("type").asInt();
              double value = m.path("value").asDouble();
              int unit = m.path("unit").asInt();
              double realValue = value * Math.pow(10, unit);
              switch (type) {
                case 1 -> weight = realValue;
                case 4 -> height = realValue * 100; // m → cm
                case 9 -> diastolic = (int) realValue;
                case 10 -> systolic = (int) realValue;
                case 11 -> hr = (int) realValue;
                case 54 -> spo2 = (int) realValue;
                case 71 -> temp = realValue;
                default -> { /* skip */ }
              }
            }
          }
          Double bmi = (weight != null && height != null && height > 0)
              ? weight / Math.pow(height / 100, 2) : null;
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              hr, null, null, null, null, null, null, null, null, null,
              weight, height, bmi, systolic, diastolic, null, temp, spo2, null,
              null, null, null, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    return records;
  }

  @Override
  public List<String> supportedMetrics() {
    return List.of("weight", "height", "bmi", "heart_rate", "blood_pressure",
        "body_temperature", "spo2");
  }

  // ===== 辅助方法 =====
  private OAuthTokenExchange parseWithingsToken(String json) {
    try {
      JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
      JsonNode body = root.path("body");
      if (body.isMissingNode()) {
        return http.parseTokenResponse(json, json);
      }
      String accessToken = body.path("access_token").asText("");
      String refreshToken = body.path("refresh_token").asText("");
      int expiresIn = body.path("expires_in").asInt(3600);
      String tokenType = body.path("token_type").asText("Bearer");
      Instant expiresAt = Instant.now().plusSeconds(expiresIn);
      return new OAuthTokenExchange(accessToken, refreshToken, expiresAt, tokenType, json);
    } catch (Exception e) {
      return http.parseTokenResponse(json, json);
    }
  }
}
