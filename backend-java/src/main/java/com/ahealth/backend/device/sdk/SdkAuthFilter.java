package com.ahealth.backend.device.sdk;

import com.ahealth.backend.common.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 开放 SDK 鉴权 Filter。
 *
 * <p>拦截 /api/devices/sdk/** 路径，从 X-SDK-API-Key header 提取 API Key，
 * 调用 {@link SdkKeyService#validateKey} 校验，校验成功后将 user_id 存入 request attribute
 * "sdkUserId"，Controller 通过 {@link #currentSdkUserId(HttpServletRequest)} 获取。
 *
 * <p>未配置（无 SDK Key 表）或路径不匹配时直接放行，由 Spring Security 路由兜底。
 */
@Component
@ConditionalOnBean(SdkKeyService.class)
public class SdkAuthFilter extends OncePerRequestFilter {

  public static final String HEADER_NAME = "X-SDK-API-Key";
  public static final String REQUEST_ATTR_USER_ID = "sdkUserId";
  private static final String PROTECTED_PATH_PREFIX = "/api/devices/sdk/";

  private final SdkKeyService sdkKeyService;

  public SdkAuthFilter(SdkKeyService sdkKeyService) {
    this.sdkKeyService = sdkKeyService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
  ) throws ServletException, IOException {
    String path = request.getRequestURI();
    if (path == null || !path.startsWith(PROTECTED_PATH_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    // /api/devices/sdk/keys 端点（管理 API Key 本身）走标准用户 Token 鉴权，不走 SDK Key
    // /api/devices/sdk/schema 端点公开访问，无需鉴权
    if (path.startsWith("/api/devices/sdk/keys") || path.startsWith("/api/devices/sdk/schema")) {
      filterChain.doFilter(request, response);
      return;
    }

    String apiKey = request.getHeader(HEADER_NAME);
    try {
      long userId = sdkKeyService.validateKey(apiKey);
      request.setAttribute(REQUEST_ATTR_USER_ID, userId);
      filterChain.doFilter(request, response);
    } catch (ApiException ex) {
      response.setStatus(ex.getStatus().value());
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"error\":\"" + ex.getMessage() + "\"}");
    } catch (Exception ex) {
      response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"error\":\"SDK 鉴权内部错误\"}");
    }
  }

  /** Controller 从 request 中获取 SDK 鉴权后的 user_id。 */
  public static long currentSdkUserId(HttpServletRequest request) {
    Object attr = request.getAttribute(REQUEST_ATTR_USER_ID);
    if (attr instanceof Long userId) {
      return userId;
    }
    throw new ApiException(HttpStatus.UNAUTHORIZED, "SDK 鉴权未通过");
  }
}
