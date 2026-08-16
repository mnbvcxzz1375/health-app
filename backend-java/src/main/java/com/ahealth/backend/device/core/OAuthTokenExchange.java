package com.ahealth.backend.device.core;

import java.time.Instant;

/**
 * OAuth token 交换结果。
 * 由各 OAuth Provider 的 exchangeCode()/refreshToken() 返回。
 * 由 TokenEncryptionService 加密后存入 device_bindings.access_token_enc / refresh_token_enc。
 */
public record OAuthTokenExchange(
    String accessToken,
    String refreshToken,
    Instant expiresAt,                  // access_token 过期时间
    String tokenType,                   // 通常 "Bearer"
    String rawJson                      // 厂商返回的原始 JSON（保留以便排查）
) {
  /** 是否已过期(或剩余时间 < 60s) */
  public boolean isExpiringSoon() {
    if (expiresAt == null) return false;
    return expiresAt.isBefore(Instant.now().plusSeconds(60));
  }
}
