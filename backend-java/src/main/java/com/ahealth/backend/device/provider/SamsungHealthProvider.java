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
 * Samsung Health Provider（OAuth 2.0 实现）。
 *
 * <p>API 文档: https://developer.samsung.com/health/android/data/api
 * Samsung Health 通过 Samsung Health Platform SDK 暴露数据，需要合作伙伴资质。
 * Samsung Health Platform 需要合作伙伴 SDK/端点；仓库不提供伪造的公共 OAuth 端点。
 * 只有在部署方显式注入真实端点和凭证后才会启用该 Provider。
 */
@Component
public class SamsungHealthProvider extends AbstractOAuthProvider {

  private final String clientId;
  private final String clientSecret;
  private final String authUrl;
  private final String tokenUrl;
  private final String apiBase;
  private final OAuthHttpHelper http;

  public SamsungHealthProvider(
      @Value("${device.providers.samsung-health.client-id:}") String clientId,
      @Value("${device.providers.samsung-health.client-secret:}") String clientSecret,
      @Value("${device.providers.samsung-health.auth-url:}") String authUrl,
      @Value("${device.providers.samsung-health.token-url:}") String tokenUrl,
      @Value("${device.providers.samsung-health.api-base:}") String apiBase,
      OAuthHttpHelper http
  ) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authUrl = authUrl;
    this.tokenUrl = tokenUrl;
    this.apiBase = apiBase;
    this.http = http;
  }

  @Override public String providerName() { return "samsung-health"; }
  @Override public String displayName() { return "Samsung Health"; }
  @Override public String deviceType() { return "watch"; }
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
        + "&scope=step_count,heart_rate,sleep,exercise,weight"
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

    // 1) 每日步数 / 卡路里 / 距离
    try {
      JsonNode steps = http.bearerGet(
          apiBase + "/v1/users/me/steps?startDate=" + from + "&endDate=" + to, accessToken);
      JsonNode arr = steps.isArray() ? steps : steps.path("data");
      if (arr.isArray()) {
        for (JsonNode item : arr) {
          Instant recordedAt = parseDate(item.path("date").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("samsung-health", "Samsung Watch", recordedAt);
          Integer stepCount = readInt(item, "step_count");
          Integer calories = readInt(item, "calorie");
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              null, null, null, stepCount, null, null, calories, null, null, null,
              null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    // 2) 心率
    try {
      JsonNode hr = http.bearerGet(
          apiBase + "/v1/users/me/heart_rate?startDate=" + from + "&endDate=" + to, accessToken);
      JsonNode arr = hr.isArray() ? hr : hr.path("data");
      if (arr.isArray()) {
        for (JsonNode item : arr) {
          Instant recordedAt = parseDate(item.path("date").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("samsung-health", "Samsung Watch", recordedAt);
          Integer hrAvg = readInt(item, "heart_rate");
          Integer hrRest = readInt(item, "resting_heart_rate");
          records.add(new UnifiedHealthRecord(
              empty.provider(), empty.sourceDevice(), recordedAt,
              hrAvg, hrRest, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null));
        }
      }
    } catch (Exception ignored) { /* skip */ }

    // 3) 睡眠
    try {
      JsonNode sleep = http.bearerGet(
          apiBase + "/v1/users/me/sleep?startDate=" + from + "&endDate=" + to, accessToken);
      JsonNode arr = sleep.isArray() ? sleep : sleep.path("data");
      if (arr.isArray()) {
        for (JsonNode item : arr) {
          Instant recordedAt = parseDate(item.path("date").asText(""));
          if (recordedAt == null) continue;
          UnifiedHealthRecord empty = UnifiedHealthRecord.empty("samsung-health", "Samsung Watch", recordedAt);
          Integer totalSec = readInt(item, "total_sleep_time");
          Integer deepSec = readInt(item, "deep_sleep_time");
          Integer remSec = readInt(item, "rem_sleep_time");
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
        "deep_sleep", "rem_sleep");
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
