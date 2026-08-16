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
 * Dexcom Provider（OAuth 2.0 实现）。
 *
 * <p>API 文档: https://developer.dexcom.com/docs
 * Dexcom 专注连续血糖监测（CGM）。
 * 端点:
 * - 授权: https://api.dexcom.com/v2/oauth2/login
 * - Token: https://api.dexcom.com/v2/oauth2/token
 * - 血糖数据: /v2/users/self/egvs
 */
@Component
public class DexcomProvider extends AbstractOAuthProvider {

  private final String clientId;
  private final String clientSecret;
  private final String authUrl;
  private final String tokenUrl;
  private final String apiBase;
  private final OAuthHttpHelper http;

  public DexcomProvider(
      @Value("${device.providers.dexcom.client-id:}") String clientId,
      @Value("${device.providers.dexcom.client-secret:}") String clientSecret,
      @Value("${device.providers.dexcom.auth-url:https://api.dexcom.com/v2/oauth2/login}") String authUrl,
      @Value("${device.providers.dexcom.token-url:https://api.dexcom.com/v2/oauth2/token}") String tokenUrl,
      @Value("${device.providers.dexcom.api-base:https://api.dexcom.com}") String apiBase,
      OAuthHttpHelper http
  ) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authUrl = authUrl;
    this.tokenUrl = tokenUrl;
    this.apiBase = apiBase;
    this.http = http;
  }

  @Override public String providerName() { return "dexcom"; }
  @Override public String displayName() { return "Dexcom CGM"; }
  @Override public String deviceType() { return "cgm"; }
  @Override public boolean isConfigured() {
    return !clientId.isBlank() && !clientSecret.isBlank();
  }

  @Override
  protected String buildAuthorizeUrl(long userId, String redirectUri) {
    return authUrl
        + "?response_type=code"
        + "&client_id=" + OAuthHttpHelper.urlEncode(clientId)
        + "&redirect_uri=" + OAuthHttpHelper.urlEncode(redirectUri)
        + "&scope=offline_access"
        + "&state=" + userId + "-" + Long.toHexString(System.currentTimeMillis());
  }

  @Override
  protected OAuthTokenExchange doExchangeCode(String code, String redirectUri) {
    Map<String, String> params = Map.of(
        "grant_type", "authorization_code",
        "code", code,
        "redirect_uri", redirectUri,
        "client_id", clientId,
        "client_secret", clientSecret
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
        "refresh_token", refreshToken,
        "client_id", clientId,
        "client_secret", clientSecret
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

    // 血糖 EGVs (Estimated Glucose Values)
    try {
      JsonNode resp = http.bearerGet(
          apiBase + "/v2/users/self/egvs?startDate=" + from + "&endDate=" + to, accessToken);
      JsonNode egvs = resp.path("egvs");
      if (egvs.isArray()) {
        for (JsonNode item : egvs) {
          String timestamp = item.path("displayTime").asText(item.path("systemTime").asText(""));
          Instant recordedAt = parseDexcomDateTime(timestamp);
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("dexcom", "Dexcom G6/G7", recordedAt);
          // Dexcom 返回 mg/dL，转为 mmol/L（÷18.018）
          Integer mgdl = readInt(item, "value");
          Double mmol = mgdl != null ? mgdl / 18.018 : null;
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              null, null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, mmol, null, null, null,
              null, null, null, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    return records;
  }

  @Override
  public List<String> supportedMetrics() {
    return List.of("blood_glucose");
  }

  // ===== 辅助方法 =====
  private static Instant parseDexcomDateTime(String dateTime) {
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
}
