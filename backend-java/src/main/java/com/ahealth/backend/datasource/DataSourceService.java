package com.ahealth.backend.datasource;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 数据来源元信息查询服务。
 *
 * <p>查询 data_sources 表，并对每条记录动态统计目标表的当前记录数，
 * 用于在前端「我的 → 数据来源」页面展示合规信息与数据规模。
 */
@Service
public class DataSourceService {
  private final JdbcTemplate jdbcTemplate;

  public DataSourceService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 列出所有数据来源，附带目标表的最新记录数。 */
  public List<DataSourceDtos.DataSourceItem> listDataSources() {
    List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
        """
        SELECT id, source_name, source_type, target_table, record_count,
               license, reference_url, citation, last_updated
        FROM data_sources
        ORDER BY source_type ASC, source_name ASC
        """
    );

    List<DataSourceDtos.DataSourceItem> items = new ArrayList<>(rows.size());
    for (java.util.Map<String, Object> row : rows) {
      String targetTable = stringOf(row.get("target_table"));
      int dynamicCount = countTable(targetTable);
      items.add(new DataSourceDtos.DataSourceItem(
          longOf(row.get("id")),
          stringOf(row.get("source_name")),
          stringOf(row.get("source_type")),
          targetTable,
          dynamicCount > 0 ? dynamicCount : intOf(row.get("record_count")),
          stringOf(row.get("license")),
          stringOf(row.get("reference_url")),
          stringOf(row.get("citation")),
          toLocalDateTime(row.get("last_updated"))
      ));
    }
    return items;
  }

  /** 汇总统计：总数、总记录数、最近更新时间、按类型分组。 */
  public DataSourceDtos.DataSourceSummary getSummary() {
    List<DataSourceDtos.DataSourceItem> items = listDataSources();
    int open = 0, academic = 0, manual = 0, api = 0;
    long total = 0;
    LocalDateTime latest = null;
    for (DataSourceDtos.DataSourceItem item : items) {
      total += item.recordCount();
      switch (item.sourceType()) {
        case "open" -> open++;
        case "academic" -> academic++;
        case "manual" -> manual++;
        case "api" -> api++;
        default -> {}
      }
      if (item.lastUpdated() != null && (latest == null || item.lastUpdated().isAfter(latest))) {
        latest = item.lastUpdated();
      }
    }
    return new DataSourceDtos.DataSourceSummary(
        items.size(),
        total,
        latest,
        new DataSourceDtos.CountsByType(open, academic, manual, api)
    );
  }

  /** 安全统计目标表记录数，表不存在或查询失败返回 0。 */
  private int countTable(String tableName) {
    if (tableName == null || tableName.isBlank()) return 0;
    // 仅允许字母数字下划线，防止 SQL 注入
    if (!tableName.matches("^[a-zA-Z0-9_]+$")) return 0;
    try {
      Integer count = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM " + tableName,
          Integer.class
      );
      return count == null ? 0 : count;
    } catch (Exception e) {
      return 0;
    }
  }

  private static long longOf(Object v) {
    if (v == null) return 0L;
    if (v instanceof Number n) return n.longValue();
    try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
  }

  private static int intOf(Object v) {
    if (v == null) return 0;
    if (v instanceof Number n) return n.intValue();
    try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
  }

  private static String stringOf(Object v) {
    return v == null ? "" : v.toString();
  }

  private static LocalDateTime toLocalDateTime(Object v) {
    if (v == null) return null;
    if (v instanceof LocalDateTime ldt) return ldt;
    if (v instanceof Timestamp ts) return ts.toLocalDateTime();
    if (v instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
    return null;
  }
}
