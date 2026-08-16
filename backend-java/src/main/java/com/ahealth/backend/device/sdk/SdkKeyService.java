package com.ahealth.backend.device.sdk;

import com.ahealth.backend.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 开放 SDK API Key 管理服务。
 *
 * <p>第三方集成方在设备聚合平台注册后会获得一个 API Key（前缀 'ahsdk_' + 32位 hex）。
 * 第三方调用 /api/devices/sdk/** 端点时通过 X-SDK-API-Key header 鉴权。
 *
 * <p>API Key 在 device_sdk_keys 表中存储 SHA-256 hash，明文仅在创建时返回一次。
 * 这样即使数据库泄露也无法直接拿到明文 Key。
 */
@Service
public class SdkKeyService {

  private static final String KEY_PREFIX = "ahsdk_";
  private static final int KEY_RANDOM_BYTES = 16; // 32 hex chars

  private final JdbcTemplate jdbc;
  private final SecureRandom random = new SecureRandom();

  public SdkKeyService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 为指定用户创建新的 SDK API Key，返回明文（仅此一次）。 */
  public SdkKeyCreation createKey(long userId, String appName, String contactEmail) {
    if (appName == null || appName.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "appName 不能为空");
    }
    String plainKey = generatePlainKey();
    String keyHash = sha256(plainKey);
    String keyPreview = plainKey.substring(0, KEY_PREFIX.length() + 6) + "...";

    jdbc.update(
        "INSERT INTO device_sdk_keys (user_id, app_name, contact_email, key_hash, key_preview, "
            + "status, created_at, updated_at) VALUES (?,?,?,?,?,?,NOW(),NOW())",
        userId, appName, contactEmail == null ? "" : contactEmail,
        keyHash, keyPreview, "active"
    );

    return new SdkKeyCreation(plainKey, keyPreview, appName);
    }

  /** 列出指定用户的所有 SDK Key（不返回明文）。 */
  public List<SdkKeyItem> listKeys(long userId) {
    return jdbc.query(
        "SELECT id, app_name, contact_email, key_preview, status, created_at, last_used_at "
            + "FROM device_sdk_keys WHERE user_id = ? ORDER BY id DESC",
        (rs, rowNum) -> new SdkKeyItem(
            rs.getInt("id"),
            rs.getString("app_name"),
            rs.getString("contact_email"),
            rs.getString("key_preview"),
            rs.getString("status"),
            getLocalDateTime(rs, "created_at"),
            getLocalDateTime(rs, "last_used_at")
        ),
        userId
    );
  }

  /** 撤销指定 Key。 */
  public void revokeKey(long userId, int keyId) {
    int affected = jdbc.update(
        "UPDATE device_sdk_keys SET status='revoked', updated_at=NOW() WHERE id=? AND user_id=?",
        keyId, userId);
    if (affected == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "SDK Key 不存在或无权操作");
    }
  }

  /**
   * 校验 API Key 并返回所属 user_id。
   * 校验失败抛 401。成功时更新 last_used_at。
   */
  public long validateKey(String plainKey) {
    if (plainKey == null || !plainKey.startsWith(KEY_PREFIX)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "X-SDK-API-Key 缺失或格式不合法");
    }
    String hash = sha256(plainKey);
    List<Long> ids = jdbc.query(
        "SELECT id, user_id FROM device_sdk_keys WHERE key_hash = ? AND status = 'active'",
        (rs, rowNum) -> rs.getLong("user_id"),
        hash
    );
    if (ids.isEmpty()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "X-SDK-API-Key 无效或已被撤销");
    }
    long userId = ids.get(0);
    // 更新 last_used_at（best-effort）
    try {
      jdbc.update("UPDATE device_sdk_keys SET last_used_at=NOW() WHERE key_hash=?", hash);
    } catch (Exception ignored) { /* skip */ }
    return userId;
  }

  // ===== 内部辅助 =====
  private String generatePlainKey() {
    byte[] bytes = new byte[KEY_RANDOM_BYTES];
    random.nextBytes(bytes);
    return KEY_PREFIX + HexFormat.of().formatHex(bytes);
  }

  private static String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }

  private static LocalDateTime getLocalDateTime(ResultSet rs, String col) throws SQLException {
    Timestamp ts = rs.getTimestamp(col);
    return ts == null ? null : ts.toLocalDateTime();
  }

  /** SDK Key 创建结果。 */
  public record SdkKeyCreation(String apiKey, String keyPreview, String appName) {}

  /** SDK Key 列表项。 */
  public record SdkKeyItem(
      int id, String appName, String contactEmail, String keyPreview,
      String status, LocalDateTime createdAt, LocalDateTime lastUsedAt
  ) {}
}
