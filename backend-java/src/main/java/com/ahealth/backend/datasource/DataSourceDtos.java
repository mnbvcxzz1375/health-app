package com.ahealth.backend.datasource;

import java.time.LocalDateTime;
import java.util.List;

public final class DataSourceDtos {
  private DataSourceDtos() {}

  /** 单个数据来源元信息记录。 */
  public record DataSourceItem(
      long id,
      String sourceName,
      String sourceType,        // open / academic / manual / api
      String targetTable,
      int recordCount,
      String license,
      String referenceUrl,
      String citation,
      LocalDateTime lastUpdated
  ) {}

  /** 数据来源汇总统计。 */
  public record DataSourceSummary(
      int totalSources,
      long totalRecords,
      LocalDateTime lastUpdatedAt,
      CountsByType byType
  ) {}

  public record CountsByType(
      int open,
      int academic,
      int manual,
      int api
  ) {}
}
