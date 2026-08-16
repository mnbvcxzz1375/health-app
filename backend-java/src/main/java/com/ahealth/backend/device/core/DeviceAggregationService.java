package com.ahealth.backend.device.core;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.device.model.DeviceAggregationDtos.AppleHealthSnapshotRequest;
import com.ahealth.backend.device.model.DeviceAggregationDtos.AppleHealthSnapshotResponse;
import com.ahealth.backend.device.model.DeviceAggregationDtos.AuthorizeResponse;
import com.ahealth.backend.device.model.DeviceAggregationDtos.BindingItem;
import com.ahealth.backend.device.model.DeviceAggregationDtos.ManualInputRequest;
import com.ahealth.backend.device.model.DeviceAggregationDtos.ManualInputResponse;
import com.ahealth.backend.device.model.DeviceAggregationDtos.MetricRouteResponse;
import com.ahealth.backend.device.model.DeviceAggregationDtos.OperationResult;
import com.ahealth.backend.device.model.DeviceAggregationDtos.ProviderInfo;
import com.ahealth.backend.device.model.DeviceAggregationDtos.SyncLogItem;
import com.ahealth.backend.device.model.UnifiedHealthRecord;
import com.ahealth.backend.device.provider.AppleHealthProvider;
import com.ahealth.backend.device.provider.ManualInputProvider;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 设备聚合平台业务逻辑层。
 *
 * <p>Controller 调 Service，Service 协调 Provider / Writer / Router / 加密服务。
 */
@Service
public class DeviceAggregationService {

  private final JdbcTemplate jdbc;
  private final DeviceProviderRegistry registry;
  private final TokenEncryptionService encryption;
  private final OAuthStateService oauthStateService;
  private final UnifiedRecordWriter writer;
  private final DeviceRouter router;
  private final ManualInputProvider manualProvider;
  private final AppleHealthProvider appleHealthProvider;
  private final String oauthRedirectBaseUrl;

  public DeviceAggregationService(
      JdbcTemplate jdbc,
      DeviceProviderRegistry registry,
      TokenEncryptionService encryption,
      OAuthStateService oauthStateService,
      UnifiedRecordWriter writer,
      DeviceRouter router,
      ManualInputProvider manualProvider,
      AppleHealthProvider appleHealthProvider,
      @Value("${device.aggregation.oauth.redirect-base-url:http://127.0.0.1:4173}") String oauthRedirectBaseUrl
  ) {
    this.jdbc = jdbc;
    this.registry = registry;
    this.encryption = encryption;
    this.oauthStateService = oauthStateService;
    this.writer = writer;
    this.router = router;
    this.manualProvider = manualProvider;
    this.appleHealthProvider = appleHealthProvider;
    this.oauthRedirectBaseUrl = stripTrailingSlash(oauthRedirectBaseUrl);
  }

  // ===== Provider 列表 =====

  public List<ProviderInfo> listProviders() {
    return registry.toProviderInfoList();
  }

  // ===== 绑定管理 =====

  public List<BindingItem> listBindings(long userId) {
    return jdbc.query(
        "SELECT id, provider, display_name, device_type, status, last_sync_at, "
            + "last_sync_status, last_error FROM device_bindings WHERE user_id = ? ORDER BY id DESC",
        (rs, rowNum) -> new BindingItem(
            rs.getInt("id"),
            rs.getString("provider"),
            rs.getString("display_name"),
            rs.getString("device_type"),
            rs.getString("status"),
            getLocalDateTime(rs, "last_sync_at"),
            rs.getString("last_sync_status"),
            rs.getString("last_error")
        ),
        userId
    );
  }

  public AuthorizeResponse startOAuth(long userId, String providerName) {
    DeviceProvider provider = registry.getProvider(providerName);
    String redirectUri = oauthRedirectBaseUrl + "/devices/oauth/callback/" + providerName;
    String url = provider.getAuthorizeUrl(userId, redirectUri);
    String state = oauthStateService.issue(providerName, userId);
    return new AuthorizeResponse(providerName, oauthStateService.replaceState(url, state));
  }

  public OperationResult handleOAuthCallback(String providerName, String code, String state) {
    DeviceProvider provider = registry.getProvider(providerName);
    // state 中编码了 userId（前端发起授权时拼接），格式："{userId}-{nonce}"
    if (code == null || code.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "OAuth code 参数缺失");
    }
    long userId = oauthStateService.consume(providerName, state);

    OAuthTokenExchange token = provider.exchangeCode(code, oauthRedirectBaseUrl + "/devices/oauth/callback/" + providerName);

    // 加密 token 并存储
    encryption.ensureConfigured();
    byte[] accessEnc = encryption.encrypt(token.accessToken());
    byte[] refreshEnc = encryption.encrypt(token.refreshToken());

    String displayName = provider.displayName();
    String deviceType = provider.deviceType();

    // upsert binding
    Integer existing = jdbc.queryForObject(
        "SELECT id FROM device_bindings WHERE user_id = ? AND provider = ?",
        Integer.class, userId, providerName);

    if (existing != null) {
      jdbc.update(
          "UPDATE device_bindings SET access_token_enc=?, refresh_token_enc=?, token_expires_at=?, "
              + "status='connected', last_error=NULL, updated_at=NOW() WHERE id=?",
          accessEnc, refreshEnc, toLocalDateTime(token.expiresAt()), existing);
    } else {
      jdbc.update(
          "INSERT INTO device_bindings (user_id, provider, external_user_id, display_name, device_type, "
              + "status, access_token_enc, refresh_token_enc, token_expires_at, created_at, updated_at) "
              + "VALUES (?,?,?,?,?,?,?,?,NOW(),NOW())",
          userId, providerName, "", displayName, deviceType, "connected", accessEnc, refreshEnc,
          toLocalDateTime(token.expiresAt()));
    }

    return new OperationResult(true, "已绑定 " + displayName);
  }

  public OperationResult deleteBinding(long userId, int bindingId) {
    int affected = jdbc.update(
        "UPDATE device_bindings SET status='disconnected', access_token_enc=NULL, "
            + "refresh_token_enc=NULL, updated_at=NOW() WHERE id=? AND user_id=?",
        bindingId, userId);
    if (affected == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "绑定不存在或无权操作");
    }
    return new OperationResult(true, "已解绑");
  }

  // ===== 同步 =====

  public OperationResult syncBinding(long userId, int bindingId) {
    BindingRow row = findBinding(userId, bindingId);
    DeviceProvider provider = registry.getProvider(row.provider());

    // 取 token，必要时刷新
    OAuthTokenExchange token = decryptToken(row);
    if (token != null && token.isExpiringSoon() && token.refreshToken() != null) {
      try {
        token = provider.refreshToken(token.refreshToken());
        saveRefreshedToken(row.id(), token);
      } catch (Exception ex) {
        // 刷新失败继续用旧 token
      }
    }

    LocalDateTime startedAt = LocalDateTime.now();
    int recordsPulled = 0, recordsWritten = 0;
    String status = "success";
    String errorMsg = null;

    try {
      LocalDate to = LocalDate.now();
      LocalDate from = to.minusDays(1);
      List<UnifiedHealthRecord> records = provider.pullData(userId, row.externalUserId(), token, from, to);
      recordsPulled = records.size();
      recordsWritten = writer.writeRecords(userId, records);
      if (recordsPulled == 0) status = "partial";
    } catch (Exception ex) {
      status = "failed";
      errorMsg = ex.getMessage();
    }

    LocalDateTime endedAt = LocalDateTime.now();
    jdbc.update(
        "INSERT INTO device_sync_logs (binding_id, user_id, sync_started_at, sync_ended_at, "
            + "status, records_pulled, records_written, error_message, details_json, created_at) "
            + "VALUES (?,?,?,?,?,?,?,?,NULL,NOW())",
        bindingId, userId, startedAt, endedAt, status, recordsPulled, recordsWritten, errorMsg);

    jdbc.update(
        "UPDATE device_bindings SET last_sync_at=NOW(), last_sync_status=?, last_error=?, "
            + "status='connected', updated_at=NOW() WHERE id=?",
        status, errorMsg, bindingId);

    String msg = status.equals("success")
        ? "同步成功，写入 " + recordsWritten + " 条"
        : status.equals("partial")
            ? "同步完成（无新数据）"
            : "同步失败：" + (errorMsg == null ? "未知错误" : errorMsg);
    return new OperationResult(!"failed".equals(status), msg);
  }

  // ===== 路由 =====

  public MetricRouteResponse route(long userId, String metric) {
    return router.route(userId, metric);
  }

  // ===== 手动输入 =====

  public ManualInputResponse pushManual(long userId, ManualInputRequest req) {
    Instant recordedAt = req.recordedAt() != null && !req.recordedAt().isBlank()
        ? Instant.parse(req.recordedAt())
        : Instant.now();
    manualProvider.pushData(userId, req.metric(), req.value(), recordedAt);
    return new ManualInputResponse(true, req.metric(), req.value(), recordedAt.toString());
  }

  // ===== Apple Health 快照 =====

  public AppleHealthSnapshotResponse receiveAppleHealthSnapshot(long userId, AppleHealthSnapshotRequest snapshot) {
    appleHealthProvider.receiveSnapshot(userId, snapshot);
    return new AppleHealthSnapshotResponse(true, Instant.now().toString(), "已接收 Apple Health 快照");
  }

  // ===== 同步日志 =====

  public List<SyncLogItem> listSyncLogs(long userId, int bindingId) {
    return jdbc.query(
        "SELECT id, binding_id, sync_started_at, sync_ended_at, status, records_pulled, "
            + "records_written, error_message FROM device_sync_logs "
            + "WHERE user_id = ? AND binding_id = ? ORDER BY id DESC LIMIT 50",
        (rs, rowNum) -> new SyncLogItem(
            rs.getInt("id"),
            rs.getInt("binding_id"),
            getLocalDateTime(rs, "sync_started_at"),
            getLocalDateTime(rs, "sync_ended_at"),
            rs.getString("status"),
            rs.getInt("records_pulled"),
            rs.getInt("records_written"),
            rs.getString("error_message")
        ),
        userId, bindingId
    );
  }

  // ===== 内部辅助 =====

  private BindingRow findBinding(long userId, int bindingId) {
    List<BindingRow> rows = jdbc.query(
        "SELECT id, provider, external_user_id, access_token_enc, refresh_token_enc, token_expires_at "
            + "FROM device_bindings WHERE id = ? AND user_id = ?",
        (rs, rowNum) -> new BindingRow(
            rs.getInt("id"),
            rs.getString("provider"),
            rs.getString("external_user_id"),
            rs.getBytes("access_token_enc"),
            rs.getBytes("refresh_token_enc"),
            getLocalDateTime(rs, "token_expires_at")
        ),
        bindingId, userId
    );
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "绑定不存在或无权操作");
    }
    return rows.get(0);
  }

  private OAuthTokenExchange decryptToken(BindingRow row) {
    if (row.accessTokenEnc() == null) return null;
    encryption.ensureConfigured();
    String access = encryption.decrypt(row.accessTokenEnc());
    String refresh = encryption.decrypt(row.refreshTokenEnc());
    Instant expiresAt = row.tokenExpiresAt() != null
        ? row.tokenExpiresAt().atZone(ZoneId.systemDefault()).toInstant()
        : null;
    return new OAuthTokenExchange(access, refresh, expiresAt, "Bearer", null);
  }

  private void saveRefreshedToken(int bindingId, OAuthTokenExchange token) {
    encryption.ensureConfigured();
    byte[] accessEnc = encryption.encrypt(token.accessToken());
    byte[] refreshEnc = encryption.encrypt(token.refreshToken());
    jdbc.update(
        "UPDATE device_bindings SET access_token_enc=?, refresh_token_enc=?, token_expires_at=?, "
            + "updated_at=NOW() WHERE id=?",
        accessEnc, refreshEnc, toLocalDateTime(token.expiresAt()), bindingId);
  }

  private static String stripTrailingSlash(String url) {
    if (url == null) return "";
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static LocalDateTime toLocalDateTime(Instant instant) {
    if (instant == null) return null;
    return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
  }

  private static LocalDateTime getLocalDateTime(ResultSet rs, String col) throws SQLException {
    Timestamp ts = rs.getTimestamp(col);
    return ts == null ? null : ts.toLocalDateTime();
  }

  /** 内部绑定行（用于同步流程）。 */
  private record BindingRow(
      int id,
      String provider,
      String externalUserId,
      byte[] accessTokenEnc,
      byte[] refreshTokenEnc,
      LocalDateTime tokenExpiresAt
  ) {}
}
