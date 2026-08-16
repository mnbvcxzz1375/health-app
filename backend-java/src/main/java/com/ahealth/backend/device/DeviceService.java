package com.ahealth.backend.device;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import com.ahealth.backend.common.TimeFormats;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @deprecated 已被 {@link com.ahealth.backend.device.core.DeviceAggregationService} 取代。
 *             仅供 {@link DeviceController}（/api/devices/legacy）使用以保持向后兼容。
 */
@Deprecated
@Service
public class DeviceService {
  private final JdbcTemplate jdbcTemplate;

  public DeviceService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<DeviceDtos.DeviceItem> list() {
    long userId = CurrentUser.requireUserId();
    return jdbcTemplate.query(
        """
        SELECT id, label, name, brand, model, device_type, connected, battery, last_sync_at
        FROM devices
        WHERE user_id = ?
        ORDER BY id DESC
        """,
        (rs, rowNum) -> new DeviceDtos.DeviceItem(
            rs.getLong("id"),
            firstNonBlank(rs.getString("name"), rs.getString("label"), "未命名设备"),
            firstNonBlank(rs.getString("brand"), ""),
            firstNonBlank(rs.getString("model"), ""),
            firstNonBlank(rs.getString("device_type"), "other"),
            rs.getBoolean("connected"),
            rs.getInt("battery"),
            TimeFormats.toIso(rs.getObject("last_sync_at", LocalDateTime.class))
        ),
        userId
    );
  }

  @Transactional
  public DeviceDtos.DeviceItem create(DeviceDtos.CreateDeviceRequest request) {
    long userId = CurrentUser.requireUserId();
    String brand = request.brand().trim();
    String model = request.model().trim();
    String name = request.name() == null || request.name().isBlank()
        ? (brand + " " + model).trim()
        : request.name().trim();
    if (name.isBlank()) {
      name = "未命名设备";
    }
    String type = switch (String.valueOf(request.type())) {
      case "watch", "band", "ring", "other" -> request.type();
      default -> "other";
    };

    jdbcTemplate.update(
        """
        INSERT INTO devices (user_id, label, name, brand, model, device_type, connected, battery, last_sync_at)
        VALUES (?, ?, ?, ?, ?, ?, 1, 100, NOW())
        """,
        userId,
        name,
        name,
        brand,
        model,
        type
    );
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return getOne(userId, id == null ? 0L : id);
  }

  @Transactional
  public DeviceDtos.DeviceItem sync(long id) {
    long userId = CurrentUser.requireUserId();
    Map<String, Object> row = findRow(userId, id);
    int battery = row.get("battery") instanceof Number number ? number.intValue() : 100;
    // Battery is device telemetry, not a sync-side simulation. Preserve the last
    // reported value until the provider sends a fresh battery reading.
    int nextBattery = Math.max(0, Math.min(100, battery));
    jdbcTemplate.update(
        """
        UPDATE devices
        SET connected = 1, battery = ?, last_sync_at = NOW()
        WHERE id = ? AND user_id = ?
        """,
        nextBattery,
        id,
        userId
    );
    return getOne(userId, id);
  }

  public void delete(long id) {
    long userId = CurrentUser.requireUserId();
    findRow(userId, id);
    jdbcTemplate.update("DELETE FROM devices WHERE id = ? AND user_id = ?", id, userId);
  }

  private DeviceDtos.DeviceItem getOne(long userId, long id) {
    Map<String, Object> row = findRow(userId, id);
    return new DeviceDtos.DeviceItem(
        longValue(row.get("id")),
        firstNonBlank((String) row.get("name"), (String) row.get("label"), "未命名设备"),
        firstNonBlank((String) row.get("brand"), ""),
        firstNonBlank((String) row.get("model"), ""),
        firstNonBlank((String) row.get("device_type"), "other"),
        row.get("connected") instanceof Number number && number.intValue() == 1,
        row.get("battery") instanceof Number number ? number.intValue() : 100,
        TimeFormats.toIso((LocalDateTime) row.get("last_sync_at"))
    );
  }

  private Map<String, Object> findRow(long userId, long id) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        """
        SELECT id, label, name, brand, model, device_type, connected, battery, last_sync_at
        FROM devices
        WHERE id = ? AND user_id = ?
        LIMIT 1
        """,
        id,
        userId
    );
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "设备不存在");
    }
    return rows.get(0);
  }

  private long longValue(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }
}
