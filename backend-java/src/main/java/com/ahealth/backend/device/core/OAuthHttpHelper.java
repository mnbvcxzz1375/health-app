package com.ahealth.backend.device.core;

import com.ahealth.backend.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * OAuth Provider 共用的 HTTP 工具：
 * - {@link #formPost} 提交 application/x-www-form-urlencoded 请求
 * - {@link #bearerGet} 用 Bearer token GET JSON
 * - {@link #bearerPost} 用 Bearer token POST JSON
 * - {@link #parseTokenResponse} 解析厂商 token 端点响应为 {@link OAuthTokenExchange}
 */
@Component
public class OAuthHttpHelper {

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public OAuthHttpHelper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }

  /**
   * 提交 form-urlencoded POST 请求。
   *
   * @param url    端点 URL
   * @param formParams 表单参数（顺序不敏感）
   * @param headers 额外 header（如 Basic Auth），可为 null
   * @return 响应体字符串
   */
  public String formPost(String url, Map<String, String> formParams, Map<String, String> headers) {
    String body = encodeForm(formParams);
    HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (headers != null) {
      headers.forEach(reqBuilder::header);
    }
    return sendAndRead(reqBuilder.build(), url);
  }

  /** 用 Bearer token GET JSON。 */
  public JsonNode bearerGet(String url, String accessToken) {
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("Authorization", "Bearer " + accessToken)
        .header("Accept", "application/json")
        .GET()
        .build();
    return parseJson(sendAndRead(req, url));
  }

  /** 用 Bearer token POST JSON。 */
  public JsonNode bearerPost(String url, String accessToken, Object jsonBody) {
    String body;
    try {
      body = objectMapper.writeValueAsString(jsonBody);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "序列化请求体失败: " + e.getMessage());
    }
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("Authorization", "Bearer " + accessToken)
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();
    return parseJson(sendAndRead(req, url));
  }

  /**
   * 解析 OAuth token 端点响应为 {@link OAuthTokenExchange}。
   * 支持常见字段：access_token / refresh_token / expires_in / token_type
   */
  public OAuthTokenExchange parseTokenResponse(String json, String rawJson) {
    JsonNode node = parseJson(json);
    String accessToken = text(node, "access_token");
    String refreshToken = text(node, "refresh_token");
    String tokenType = text(node, "token_type", "Bearer");
    int expiresIn = node.path("expires_in").asInt(3600);
    Instant expiresAt = Instant.now().plusSeconds(expiresIn);
    return new OAuthTokenExchange(accessToken, refreshToken, expiresAt, tokenType, rawJson);
  }

  /** 生成 Basic Auth header value（client_id:client_secret base64 编码）。 */
  public String basicAuthHeader(String clientId, String clientSecret) {
    String raw = clientId + ":" + clientSecret;
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /** URL 编码单个参数。 */
  public static String urlEncode(String s) {
    return java.net.URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
  }

  /** 构造 application/x-www-form-urlencoded body。 */
  public static String encodeForm(Map<String, String> params) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> e : params.entrySet()) {
      if (!first) sb.append('&');
      sb.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
      first = false;
    }
    return sb.toString();
  }

  // ===== 内部辅助 =====

  private String sendAndRead(HttpRequest req, String urlForError) {
    try {
      HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      int code = resp.statusCode();
      String body = resp.body();
      if (code < 200 || code >= 300) {
        throw new ApiException(HttpStatus.BAD_GATEWAY,
            "OAuth HTTP 调用失败 [" + urlForError + "] 状态=" + code + " body=" + truncate(body, 500));
      }
      return body == null ? "" : body;
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "OAuth HTTP 调用异常 [" + urlForError + "]: " + e.getMessage());
    }
  }

  private JsonNode parseJson(String json) {
    try {
      return objectMapper.readTree(json == null ? "{}" : json);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "OAuth 响应 JSON 解析失败: " + e.getMessage());
    }
  }

  private static String text(JsonNode node, String field) {
    return text(node, field, null);
  }

  private static String text(JsonNode node, String field, String defaultValue) {
    JsonNode v = node.path(field);
    return v.isTextual() ? v.asText() : (v.isValueNode() ? v.asText() : defaultValue);
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() > max ? s.substring(0, max) + "..." : s;
  }
}
