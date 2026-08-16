package com.ahealth.backend.monitor;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import com.ahealth.backend.common.TimeFormats;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MonitorService {
  private final JdbcTemplate jdbcTemplate;

  public MonitorService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public MonitorDtos.MonitorLatestResponse getLatest() {
    long userId = CurrentUser.requireUserId();
    List<MonitorDtos.MonitorLatestResponse> rows = jdbcTemplate.query(
        """
        SELECT recorded_at, hr, sleep_score, deep_sleep_hours, awake_times, stress_score
        FROM monitor_records WHERE user_id = ?
        ORDER BY recorded_at DESC
        LIMIT 1
        """,
        (rs, rowNum) -> mapLatest(rs),
        userId
    );

    if (rows.isEmpty()) {
      return new MonitorDtos.MonitorLatestResponse(0, 0, 0, 0, 0, "");
    }
    return rows.get(0);
  }

  public MonitorDtos.MonitorTrendResponse getTrend(String metric, String range) {
    long userId = CurrentUser.requireUserId();
    String metricColumn = resolveMetricColumn(metric);
    RangeConfig rangeConfig = resolveRangeConfig(range);
    String sql = """
        SELECT DATE_FORMAT(recorded_at, '%s') AS label,
               ROUND(AVG(%s), 0) AS value,
               MIN(recorded_at) AS sort_time
        FROM monitor_records
        WHERE user_id = ? AND %s
        GROUP BY label
        ORDER BY sort_time ASC
        """.formatted(rangeConfig.format(), metricColumn, rangeConfig.whereClause());

    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
    List<String> labels = new ArrayList<>();
    List<Integer> values = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      labels.add(String.valueOf(row.get("label")));
      values.add(row.get("value") instanceof Number number ? number.intValue() : 0);
    }

    return new MonitorDtos.MonitorTrendResponse(
        labels,
        values,
        buildInsight(metric, values),
        buildSuggestion(metric)
    );
  }

  private MonitorDtos.MonitorLatestResponse mapLatest(ResultSet rs) throws SQLException {
    LocalDateTime recordedAt = rs.getObject("recorded_at", LocalDateTime.class);
    return new MonitorDtos.MonitorLatestResponse(
        rs.getInt("hr"),
        rs.getInt("sleep_score"),
        rs.getDouble("deep_sleep_hours"),
        rs.getInt("awake_times"),
        rs.getInt("stress_score"),
        TimeFormats.toIso(recordedAt)
    );
  }

  private String resolveMetricColumn(String metric) {
    return switch (metric) {
      case "hr" -> "hr";
      case "sleep" -> "sleep_score";
      case "stress" -> "stress_score";
      default -> throw new ApiException(HttpStatus.BAD_REQUEST, "metric 参数无效");
    };
  }

  private RangeConfig resolveRangeConfig(String range) {
    return switch (range) {
      case "minute" -> new RangeConfig("recorded_at >= DATE_SUB(NOW(), INTERVAL 60 MINUTE)", "%H:%i");
      case "hour" -> new RangeConfig("recorded_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)", "%H:%i");
      case "day" -> new RangeConfig("recorded_at >= DATE_SUB(CURDATE(), INTERVAL 13 DAY)", "%m-%d");
      case "month" -> new RangeConfig("recorded_at >= DATE_SUB(CURDATE(), INTERVAL 11 MONTH)", "%Y-%m");
      default -> throw new ApiException(HttpStatus.BAD_REQUEST, "range 参数无效");
    };
  }

  private String buildInsight(String metric, List<Integer> values) {
    if (values.isEmpty()) {
      return "暂无足够趋势数据。";
    }
    double average = values.stream().mapToInt(Integer::intValue).average().orElse(0);
    return switch (metric) {
      case "hr" -> average >= 78 ? "近一段时间静息心率整体偏高，恢复负荷需要关注。" : "近一段时间心率波动稳定，恢复节律较平稳。";
      case "sleep" -> average < 80 ? "睡眠评分仍有提升空间，建议优先修复晚间作息。" : "睡眠质量整体处于可接受区间，恢复基础不错。";
      case "stress" -> average >= 60 ? "压力指数有持续抬头趋势，建议降低近期训练与工作叠加负荷。" : "压力趋势整体平稳，可继续维持当前节奏。";
      default -> "趋势平稳。";
    };
  }

  private String buildSuggestion(String metric) {
    return switch (metric) {
      case "hr" -> "今天训练优先安排低到中等强度有氧，并留出拉伸恢复时间。";
      case "sleep" -> "今晚提前 30 分钟结束用屏，固定入睡时间。";
      case "stress" -> "插入 5 到 10 分钟呼吸放松或步行恢复，减少持续高压时段。";
      default -> "继续观察。";
    };
  }

  private record RangeConfig(String whereClause, String format) {}
}
