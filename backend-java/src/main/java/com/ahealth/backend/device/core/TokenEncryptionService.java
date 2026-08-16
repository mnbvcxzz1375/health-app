package com.ahealth.backend.device.core;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM OAuth token 加密服务。
 *
 * <p>密钥来源（按优先级）:
 * <ol>
 *   <li>{@code device.aggregation.encryption.key}（Base64 编码的 32 字节原始密钥）</li>
 *   <li>环境变量 {@code DEVICE_AGG_ENCRYPTION_KEY}</li>
 * </ol>
 *
 * <p>密钥为空时 {@link #ensureConfigured()} 抛 {@link IllegalStateException}，
 * Provider 在加密/解密前必须调用 {@link #ensureConfigured()} 自检。
 *
 * <p>密文格式: 12 字节 IV ‖ GCM 密文（含 16 字节 tag），整体 base64 后存储为 VARBINARY。
 */
@Component
public class TokenEncryptionService {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private static final int KEY_LENGTH_BYTES = 32; // AES-256

  private final SecretKeySpec keySpec;
  private final SecureRandom random = new SecureRandom();
  private final boolean configured;

  public TokenEncryptionService(
      @Value("${device.aggregation.encryption.key:${DEVICE_AGG_ENCRYPTION_KEY:}}") String keyB64
  ) {
    if (keyB64 == null || keyB64.isBlank()) {
      this.keySpec = null;
      this.configured = false;
      return;
    }
    byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(keyB64.trim());
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException(
          "device.aggregation.encryption.key 不是合法的 Base64 字符串", ex);
    }
    if (keyBytes.length != KEY_LENGTH_BYTES) {
      throw new IllegalStateException(
          "device.aggregation.encryption.key 解码后必须为 %d 字节，实际为 %d 字节"
              .formatted(KEY_LENGTH_BYTES, keyBytes.length));
    }
    this.keySpec = new SecretKeySpec(keyBytes, "AES");
    this.configured = true;
  }

  /** 是否已配置加密密钥。未配置时 Provider 应拒绝存储 token，但允许其他流程继续。 */
  public boolean isConfigured() {
    return configured;
  }

  /** 自检：调用前必须保证已配置。 */
  public void ensureConfigured() {
    if (!configured) {
      throw new IllegalStateException(
          "TokenEncryptionService 未配置 device.aggregation.encryption.key，"
              + "无法加解密 OAuth token。请生成 32 字节随机密钥并 Base64 编码后注入。");
    }
  }

  /** 加密明文 token，返回 base64 密文（含 IV）。 */
  public String encryptToBase64(String plain) {
    if (plain == null) return null;
    ensureConfigured();
    try {
      byte[] iv = new byte[IV_LENGTH];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[IV_LENGTH + cipherText.length];
      System.arraycopy(iv, 0, out, 0, IV_LENGTH);
      System.arraycopy(cipherText, 0, out, IV_LENGTH, cipherText.length);
      return Base64.getEncoder().encodeToString(out);
    } catch (Exception ex) {
      throw new IllegalStateException("AES-GCM 加密失败", ex);
    }
  }

  /** 解密 base64 密文（含 IV），返回明文 token。 */
  public String decryptFromBase64(String cipherB64) {
    if (cipherB64 == null || cipherB64.isEmpty()) return null;
    ensureConfigured();
    try {
      byte[] data = Base64.getDecoder().decode(cipherB64);
      if (data.length < IV_LENGTH + 1) {
        throw new IllegalArgumentException("密文长度不合法");
      }
      byte[] iv = new byte[IV_LENGTH];
      System.arraycopy(data, 0, iv, 0, IV_LENGTH);
      byte[] cipherText = new byte[data.length - IV_LENGTH];
      System.arraycopy(data, IV_LENGTH, cipherText, 0, cipherText.length);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new IllegalStateException("AES-GCM 解密失败", ex);
    }
  }

  /** 加密明文 token，返回原始字节（用于 VARBINARY 存储）。 */
  public byte[] encrypt(String plain) {
    String b64 = encryptToBase64(plain);
    return b64 == null ? null : b64.getBytes(StandardCharsets.UTF_8);
  }

  /** 解密原始字节（来自 VARBINARY 存储），返回明文 token。 */
  public String decrypt(byte[] cipher) {
    if (cipher == null || cipher.length == 0) return null;
    return decryptFromBase64(new String(cipher, StandardCharsets.UTF_8));
  }
}
