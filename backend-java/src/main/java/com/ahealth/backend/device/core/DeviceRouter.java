package com.ahealth.backend.device.core;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.device.model.DeviceAggregationDtos.MetricRouteResponse;
import com.ahealth.backend.device.model.DeviceAggregationDtos.SourceItem;
import com.ahealth.backend.device.model.DeviceMetricRoute;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * metric → device_type 自动路由层。
 *
 * <p>核心逻辑：
 * <ol>
 *   <li>查 {@code device_metric_routes} 表得 metric 的 preferred/fallback device_type</li>
 *   <li>查 {@code device_bindings} 表找该用户绑过哪些匹配的 provider</li>
 *   <li>按 status + last_sync_at 时间分类：
 *     <ul>
 *       <li>{@code connectedSources}: status=connected 且 last_sync_at 在 24h 内</li>
 *       <li>{@code staleSources}: status=connected 但 last_sync_at 超 24h，或 status=stale</li>
 *       <li>{@code availableSources}: registry 中 isConfigured=true 但未绑定的 provider</li>
 *     </ul>
 *   </li>
 *   <li>{@code manualInputSupported}: 永远 true（manual 永远可用作 fallback）</li>
 * </ol>
 *
 * <p>「之前连接过但当前没连上」的语义 = {@code staleSources}，触发前端「点此重新同步」提示。
 */
@Service
public class DeviceRouter {

  /** 超 24h 视为 stale。 */
  private static final long STALE_HOURS = 24;

  private final JdbcTemplate jdbc;
  private final DeviceProviderRegistry registry;

  public DeviceRouter(JdbcTemplate jdbc, DeviceProviderRegistry registry) {
    this.jdbc = jdbc;
    this.registry = registry;
  }

  /** 按 metric 自动路由，返回三态设备源。 */
  public MetricRouteResponse route(long userId, String metric) {
    if (metric == null || metric.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "metric 不能为空");
    }

    // 1. 查路由配置
    List<DeviceMetricRoute> routes = jdbc.query(
        "SELECT id, metric, metric_label, preferred_device_type, fallback_device_type, "
            + "pillar, icon, sort_order FROM device_metric_routes WHERE metric = ? LIMIT 1",
        (rs, rowNum) -> new DeviceMetricRoute(
            rs.getInt("id"),
            rs.getString("metric"),
            rs.getString("metric_label"),
            rs.getString("preferred_device_type"),
            rs.getString("fallback_device_type"),
            rs.getString("pillar"),
            rs.getString("icon"),
            rs.getInt("sort_order")
        ),
        metric
    );

    if (routes.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "未知的 metric: " + metric);
    }
    DeviceMetricRoute route = routes.get(0);

    // 2. 查用户绑定（按 device_type 过滤）
    List<BindingRow> bindings = findBindingsByDeviceType(userId, route.preferredDeviceType());

    // 3. 三态分类
    List<SourceItem> connected = new ArrayList<>();
    List<SourceItem> stale = new ArrayList<>();
    Set<String> boundProviders = new HashSet<>();
    LocalDateTime now = LocalDateTime.now();

    for (BindingRow b : bindings) {
      boundProviders.add(b.provider());
      SourceItem item = new SourceItem(
          b.provider(),
          resolveDisplayName(b.provider(), b.displayName()),
          b.deviceType(),
          b.status(),
          b.lastSyncAt(),
          b.displayName()
      );
      if (isStale(b, now)) {
        // 强制覆盖 status 为 stale，前端根据此标记显示「重新同步」
        stale.add(new SourceItem(item.provider(), item.displayName(), item.deviceType(),
            "stale", item.lastSyncAt(), item.bindingDisplayName()));
      } else if ("connected".equals(b.status())) {
        connected.add(item);
      } else if ("stale".equals(b.status())) {
        stale.add(item);
      }
      // disconnected 不进任何列表（用户已主动解绑）
    }

    // 4. availableSources = registry 中已配置但未绑定的同 device_type provider
    List<SourceItem> available = new ArrayList<>();
    for (DeviceProvider p : registry.getProvidersByDeviceType(route.preferredDeviceType())) {
      if (boundProviders.contains(p.providerName())) continue;
      if (!p.isAvailable()) continue;
      // 跳过 manual/apple_health/bluetooth/sdk 这种特殊 provider（不放 availableSources）
      if (isSpecialProvider(p.providerName())) continue;
      available.add(new SourceItem(
          p.providerName(),
          p.displayName(),
          p.deviceType(),
          "available",
          null,
          null
      ));
    }

    // 5. 手动输入 fallback
    boolean manualSupported = true;

    return new MetricRouteResponse(
        metric,
        route.metricLabel(),
        route.preferredDeviceType(),
        route.fallbackDeviceType(),
        route.pillar(),
        route.icon(),
        connected,
        stale,
        available,
        manualSupported
    );
  }

  // ===== 内部辅助 =====

  private List<BindingRow> findBindingsByDeviceType(long userId, String deviceType) {
    return jdbc.query(
        "SELECT id, provider, display_name, device_type, status, last_sync_at "
            + "FROM device_bindings WHERE user_id = ? AND device_type = ? "
            + "AND status IN ('connected', 'stale') ORDER BY last_sync_at DESC",
        (rs, rowNum) -> new BindingRow(
            rs.getInt("id"),
            rs.getString("provider"),
            rs.getString("display_name"),
            rs.getString("device_type"),
            rs.getString("status"),
            getLocalDateTime(rs, "last_sync_at")
        ),
        userId, deviceType
    );
  }

  private boolean isStale(BindingRow b, LocalDateTime now) {
    if (b.lastSyncAt() == null) return true;
    long hours = ChronoUnit.HOURS.between(b.lastSyncAt(), now);
    return hours >= STALE_HOURS;
  }

  private String resolveDisplayName(String providerName, String bindingDisplayName) {
    if (bindingDisplayName != null && !bindingDisplayName.isBlank()) {
      return bindingDisplayName;
    }
    try {
      return registry.getProvider(providerName).displayName();
    } catch (Exception ex) {
      return providerName;
    }
  }

  private boolean isSpecialProvider(String name) {
    return "manual".equals(name) || "apple_health".equals(name)
        || "bluetooth".equals(name) || "sdk".equals(name) || "rook".equals(name);
  }

  private static LocalDateTime getLocalDateTime(ResultSet rs, String col) throws SQLException {
    Timestamp ts = rs.getTimestamp(col);
    return ts == null ? null : ts.toLocalDateTime();
  }

  /** 内部绑定行（仅路由用）。 */
  private record BindingRow(
      int id,
      String provider,
      String displayName,
      String deviceType,
      String status,
      LocalDateTime lastSyncAt
  ) {}
}
