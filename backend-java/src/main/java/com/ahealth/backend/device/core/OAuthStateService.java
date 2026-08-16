package com.ahealth.backend.device.core;

import com.ahealth.backend.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Signs and consumes the short-lived, single-use state used by OAuth callbacks.
 * Redis is used as the replay registry when available; the in-process map is
 * only a local-development fallback and must not be relied on for a cluster.
 */
@Service
public class OAuthStateService {

  private static final Logger log = LoggerFactory.getLogger(OAuthStateService.class);
  private static final int TTL_SECONDS = 10 * 60;
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
  private static final Pattern STATE_QUERY = Pattern.compile("(?i)([?&])state=[^&#]*");

  private final byte[] secret;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Long> issued = new ConcurrentHashMap<>();
  private final StringRedisTemplate redis;

  @Autowired
  public OAuthStateService(
      @Value("${device.aggregation.oauth.state-secret:${DEVICE_OAUTH_STATE_SECRET:}}") String configuredSecret,
      StringRedisTemplate redis
  ) {
    this.redis = redis;
    if (configuredSecret == null || configuredSecret.isBlank()) {
      this.secret = new byte[32];
      random.nextBytes(this.secret);
      log.warn("DEVICE_OAUTH_STATE_SECRET 未配置，OAuth state 仅当前进程有效；生产环境请配置固定密钥");
    } else {
      this.secret = configuredSecret.trim().getBytes(StandardCharsets.UTF_8);
    }
  }

  /** Lightweight constructor for unit tests without an application Redis context. */
  public OAuthStateService(String configuredSecret) {
    this.redis = null;
    if (configuredSecret == null || configuredSecret.isBlank()) {
      this.secret = new byte[32];
      random.nextBytes(this.secret);
    } else {
      this.secret = configuredSecret.trim().getBytes(StandardCharsets.UTF_8);
    }
  }

  /** Issues a provider-bound state token carrying the authenticated user id. */
  public String issue(String provider, long userId) {
    if (provider == null || provider.isBlank() || userId <= 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "OAuth state 绑定参数无效");
    }
    long expiresAt = Instant.now().getEpochSecond() + TTL_SECONDS;
    byte[] nonce = new byte[18];
    random.nextBytes(nonce);
    String payload = "v1." + provider + "." + userId + "." + expiresAt + "."
        + URL_ENCODER.encodeToString(nonce);
    String token = URL_ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
        + "." + URL_ENCODER.encodeToString(sign(payload));
    purgeExpired();
    register(token, provider, userId, expiresAt);
    return token;
  }

  /** Validates and consumes a state token, returning its bound user id. */
  public long consume(String provider, String token) {
    if (provider == null || provider.isBlank() || token == null || token.isBlank()) {
      throw invalidState();
    }
    String[] parts = token.split("\\.", -1);
    if (parts.length != 2) {
      throw invalidState();
    }
    try {
      String payload = new String(URL_DECODER.decode(parts[0]), StandardCharsets.UTF_8);
      byte[] expected = sign(payload);
      byte[] actual = URL_DECODER.decode(parts[1]);
      if (!MessageDigest.isEqual(expected, actual)) {
        throw invalidState();
      }
      String[] fields = payload.split("\\.", -1);
      if (fields.length != 5 || !"v1".equals(fields[0]) || !provider.equals(fields[1])) {
        throw invalidState();
      }
      long userId = Long.parseLong(fields[2]);
      long expiresAt = Long.parseLong(fields[3]);
      if (userId <= 0 || expiresAt < Instant.now().getEpochSecond()) {
        throw invalidState();
      }
      if (!consumeRegistration(token, provider, userId, expiresAt)) {
        throw invalidState();
      }
      return userId;
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw invalidState();
    }
  }

  /** Replaces the provider-generated state parameter with the signed value. */
  public String replaceState(String authorizeUrl, String state) {
    if (authorizeUrl == null || authorizeUrl.isBlank() || state == null || state.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "OAuth authorize URL 或 state 无效");
    }
    String encoded = java.net.URLEncoder.encode(state, StandardCharsets.UTF_8);
    Matcher matcher = STATE_QUERY.matcher(authorizeUrl);
    if (matcher.find()) {
      return matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + "state=" + encoded));
    }
    return authorizeUrl + (authorizeUrl.contains("?") ? "&" : "?") + "state=" + encoded;
  }

  private byte[] sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new IllegalStateException("OAuth state 签名失败", ex);
    }
  }

  private void purgeExpired() {
    long now = Instant.now().getEpochSecond();
    issued.entrySet().removeIf(entry -> entry.getValue() < now);
  }

  private void register(String token, String provider, long userId, long expiresAt) {
    if (redis != null) {
      try {
        redis.opsForValue().set(
            registryKey(token),
            provider + "|" + userId + "|" + expiresAt,
            Duration.ofSeconds(Math.max(1, expiresAt - Instant.now().getEpochSecond())));
        return;
      } catch (RuntimeException ex) {
        log.warn("Redis OAuth state 注册失败，回退到当前进程注册表: {}", ex.getMessage());
      }
    }
    issued.put(token, expiresAt);
  }

  private boolean consumeRegistration(String token, String provider, long userId, long expiresAt) {
    if (redis != null) {
      try {
        String value = redis.opsForValue().getAndDelete(registryKey(token));
        return (provider + "|" + userId + "|" + expiresAt).equals(value);
      } catch (RuntimeException ex) {
        log.warn("Redis OAuth state 消费失败，尝试当前进程开发回退: {}", ex.getMessage());
        Long local = issued.get(token);
        return local != null && local == expiresAt && issued.remove(token, local);
      }
    }
    Long registered = issued.get(token);
    return registered != null && registered == expiresAt && issued.remove(token, registered);
  }

  private String registryKey(String token) {
    return "health-app:oauth-state:" + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(java.util.Arrays.copyOf(sign(token), 18));
  }

  private static ApiException invalidState() {
    return new ApiException(HttpStatus.BAD_REQUEST, "OAuth state 无效、已过期或已被使用");
  }
}
