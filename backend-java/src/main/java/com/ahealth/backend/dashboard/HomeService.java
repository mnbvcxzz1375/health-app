package com.ahealth.backend.dashboard;

import com.ahealth.backend.common.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HomeService {
  private final JdbcTemplate jdbcTemplate;

  public HomeService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public HomeDtos.HomeSummaryResponse getSummary() {
    long userId = CurrentUser.requireUserId();
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        """
        SELECT hs.*, up.name AS user_name
        FROM user_profiles up
        LEFT JOIN home_summary hs ON hs.user_id = up.id
        WHERE up.id = ?
        ORDER BY hs.summary_date DESC
        LIMIT 1
        """,
        userId
    );

    if (rows.isEmpty() || rows.get(0).get("summary_date") == null) {
      return new HomeDtos.HomeSummaryResponse(
          rows.isEmpty() ? "未命名用户" : stringValue(rows.get(0).get("user_name"), "未命名用户"),
          0,
          "待同步",
          "info",
          "暂无同步数据",
          0,
          0,
          List.of(),
          List.of()
      );
    }

    Map<String, Object> row = rows.get(0);
    return new HomeDtos.HomeSummaryResponse(
        stringValue(row.get("user_name"), "未命名用户"),
        intValue(row.get("health_score")),
        stringValue(row.get("status_badge"), ""),
        stringValue(row.get("status_badge_variant"), "default"),
        stringValue(row.get("status_summary"), ""),
        intValue(row.get("steps_target")),
        intValue(row.get("steps_now")),
        List.of(
            new HomeDtos.HomeMetric("hr", intValue(row.get("hr_value")), stringValue(row.get("hr_badge"), ""), stringValue(row.get("hr_badge_variant"), "default"), stringValue(row.get("hr_hint"), "")),
            new HomeDtos.HomeMetric("stress", intValue(row.get("stress_value")), stringValue(row.get("stress_badge"), ""), stringValue(row.get("stress_badge_variant"), "default"), stringValue(row.get("stress_hint"), "")),
            new HomeDtos.HomeMetric("hydration", intValue(row.get("hydration_ml")), stringValue(row.get("hydration_badge"), ""), stringValue(row.get("hydration_badge_variant"), "default"), stringValue(row.get("hydration_hint"), ""))
        ),
        List.of(
            stringValue(row.get("suggestion_1"), ""),
            stringValue(row.get("suggestion_2"), ""),
            stringValue(row.get("suggestion_3"), "")
        ).stream().filter(item -> !item.isBlank()).toList()
    );
  }

  private int intValue(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }

  private String stringValue(Object value, String fallback) {
    return value == null ? fallback : String.valueOf(value);
  }
}
