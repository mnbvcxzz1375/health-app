package com.ahealth.backend.datasource;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据来源 REST 端点（2 个）。
 *
 * <p>GET /api/data-sources          — 返回所有数据源列表
 * <p>GET /api/data-sources/summary  — 返回汇总统计
 */
@RestController
@RequestMapping("/api/data-sources")
public class DataSourceController {
  private final DataSourceService dataSourceService;

  public DataSourceController(DataSourceService dataSourceService) {
    this.dataSourceService = dataSourceService;
  }

  @GetMapping
  public List<DataSourceDtos.DataSourceItem> listDataSources() {
    return dataSourceService.listDataSources();
  }

  @GetMapping("/summary")
  public DataSourceDtos.DataSourceSummary getSummary() {
    return dataSourceService.getSummary();
  }
}
