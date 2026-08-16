package com.ahealth.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RagAdminController 单元测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>parseAdminUserIds 解析多种格式（含非法字符、空值、多 ID）</li>
 *   <li>requireAdmin 通过时端点正常执行</li>
 *   <li>requireAdmin 拒绝时抛 403 ApiException</li>
 *   <li>4 个端点（ingest / search / stats / delete）的权限校验调用</li>
 * </ul>
 *
 * <p>使用 Mockito mockStatic 模拟 CurrentUser.requireUserId() 静态方法。
 */
@ExtendWith(MockitoExtension.class)
class RagAdminControllerTest {

  @Mock
  private RagIngestionService ragIngestionService;
  @Mock
  private RagSearchService ragSearchService;
  @Mock
  private RagRepository ragRepository;
  @Mock
  private JdbcTemplate jdbcTemplate;

  // 构造不同 admin 配置的 controller
  private RagAdminController controllerWithAdminIds(String config) {
    return new RagAdminController(ragIngestionService, ragSearchService, ragRepository, jdbcTemplate, config);
  }

  @Test
  void parseAdminUserIdsSingleValue() {
    RagAdminController c = controllerWithAdminIds("1");
    // 触发 requireAdmin()，uid=1 应通过
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(1L);
      when(ragIngestionService.ingestByType("consult_qa"))
          .thenReturn(new RagDtos.IngestResult("consult_qa", 1, 0, "OK"));
      // 不抛异常即通过
      Object result = c.ingest("consult_qa");
      assertThat(result).isInstanceOf(Map.class);
    }
  }

  @Test
  void parseAdminUserIdsMultipleValues() {
    RagAdminController c = controllerWithAdminIds("1, 2, 3");
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(3L);
      when(ragIngestionService.ingestByType("herb_guide"))
          .thenReturn(new RagDtos.IngestResult("herb_guide", 5, 0, "OK"));
      Object result = c.ingest("herb_guide");
      assertThat(result).isInstanceOf(Map.class);
    }
  }

  @Test
  void parseAdminUserIdsWithInvalidEntries() {
    // 混合非法值：1, abc, 2, null, 3
    RagAdminController c = controllerWithAdminIds("1, abc, 2, , 3");
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(2L);
      when(ragIngestionService.ingestByType("ddi_rule"))
          .thenReturn(new RagDtos.IngestResult("ddi_rule", 3, 0, "OK"));
      Object result = c.ingest("ddi_rule");
      assertThat(result).isInstanceOf(Map.class);
    }
  }

  @Test
  void parseAdminUserIdsEmptyStringReturnsEmptySet() {
    RagAdminController c = controllerWithAdminIds("");
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(1L);
      assertThatThrownBy(() -> c.ingest("consult_qa"))
          .isInstanceOf(ApiException.class)
          .satisfies(ex -> {
            ApiException api = (ApiException) ex;
            assertThat(api.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
          });
    }
  }

  @Test
  void parseAdminUserIdsNullReturnsEmptySet() {
    RagAdminController c = controllerWithAdminIds(null);
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(1L);
      assertThatThrownBy(() -> c.ingest(null))
          .isInstanceOf(ApiException.class);
    }
  }

  @Test
  void requireAdminRejectsNonAdminUser() {
    RagAdminController c = controllerWithAdminIds("1, 2");
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(999L); // 非 admin
      assertThatThrownBy(() -> c.ingest("consult_qa"))
          .isInstanceOf(ApiException.class)
          .satisfies(ex -> {
            ApiException api = (ApiException) ex;
            assertThat(api.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(api.getMessage()).contains("管理员");
          });
      // 拒绝后不应调用 service
      verify(ragIngestionService, times(0)).ingestAll();
      verify(ragIngestionService, times(0)).ingestByType(anyString());
    }
  }

  @Test
  void ingestEmptyTypeCallsIngestAll() {
    RagAdminController c = controllerWithAdminIds("1");
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(1L);
      when(ragIngestionService.ingestAll()).thenReturn(List.of(
          new RagDtos.IngestResult("consult_qa", 10, 0, "OK"),
          new RagDtos.IngestResult("herb_guide", 5, 1, "OK")
      ));

      Object result = c.ingest(null);
      assertThat(result).isInstanceOf(Map.class);
      Map<?, ?> map = (Map<?, ?>) result;
      assertThat(map.get("success")).isEqualTo(true);
      assertThat(map.get("totalIngested")).isEqualTo(15);
      assertThat(map.get("totalFailed")).isEqualTo(1);

      verify(ragIngestionService, times(1)).ingestAll();
    }
  }

  @Test
  void ingestBlankTypeCallsIngestAll() {
    RagAdminController c = controllerWithAdminIds("1");
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(1L);
      when(ragIngestionService.ingestAll()).thenReturn(List.of());

      c.ingest("   ");
      verify(ragIngestionService, times(1)).ingestAll();
    }
  }

  @Test
  void searchEndpointRequiresAdmin() {
    RagAdminController c = controllerWithAdminIds("1");
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(1L);
      when(ragSearchService.search(eq("阿司匹林"), eq(null), eq(5)))
          .thenReturn(List.of());

      RagDtos.SearchResponse resp = c.search("阿司匹林", null, 5);
      assertThat(resp.hits()).isEmpty();
    }
  }

  @Test
  void searchEndpointRejectsNonAdmin() {
    RagAdminController c = controllerWithAdminIds("1");
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(2L);
      assertThatThrownBy(() -> c.search("test", null, 5))
          .isInstanceOf(ApiException.class);
      verify(ragSearchService, times(0)).search(anyString(), anyString(), eq(5));
    }
  }

  @Test
  void statsEndpointRequiresAdmin() {
    RagAdminController c = controllerWithAdminIds("1");
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(1L);
      when(ragRepository.repositoryType()).thenReturn("redis-stack");
      when(ragRepository.isAvailable()).thenReturn(true);
      when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
          Map.of("doc_type", "consult_qa", "cnt", 50),
          Map.of("doc_type", "herb_guide", "cnt", 30)
      ));

      Object result = c.stats();
      assertThat(result).isInstanceOf(Map.class);
      Map<?, ?> map = (Map<?, ?>) result;
      assertThat(map.get("repositoryType")).isEqualTo("redis-stack");
      assertThat(map.get("total")).isEqualTo(80);
    }
  }

  @Test
  void statsEndpointRejectsNonAdmin() {
    RagAdminController c = controllerWithAdminIds("1");
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(2L);
      assertThatThrownBy(() -> c.stats())
          .isInstanceOf(ApiException.class);
      verify(jdbcTemplate, times(0)).queryForList(anyString());
    }
  }

  @Test
  void deleteEndpointRequiresAdmin() {
    RagAdminController c = controllerWithAdminIds("1");
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(1L);

      Object result = c.delete("rag:doc:test:1");
      assertThat(result).isInstanceOf(Map.class);
      Map<?, ?> map = (Map<?, ?>) result;
      assertThat(map.get("success")).isEqualTo(true);
      assertThat(map.get("deleted")).isEqualTo("rag:doc:test:1");
      verify(ragRepository, times(1)).delete("rag:doc:test:1");
    }
  }

  @Test
  void deleteEndpointRejectsNonAdmin() {
    RagAdminController c = controllerWithAdminIds("1");
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(999L);
      assertThatThrownBy(() -> c.delete("rag:doc:test:1"))
          .isInstanceOf(ApiException.class);
      verify(ragRepository, times(0)).delete(anyString());
    }
  }

  @Test
  void statsEndpointHandlesDbFailure() {
    RagAdminController c = controllerWithAdminIds("1");
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(1L);
      when(ragRepository.repositoryType()).thenReturn("in-memory");
      when(ragRepository.isAvailable()).thenReturn(false);
      when(jdbcTemplate.queryForList(anyString())).thenThrow(new RuntimeException("表不存在"));

      Object result = c.stats();
      // 应该不抛异常，返回空 byDocType
      Map<?, ?> map = (Map<?, ?>) result;
      assertThat(map.get("repositoryType")).isEqualTo("in-memory");
      assertThat(map.get("available")).isEqualTo(false);
      assertThat(map.get("total")).isEqualTo(0);
    }
  }
}
