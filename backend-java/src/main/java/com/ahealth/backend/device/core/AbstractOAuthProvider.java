package com.ahealth.backend.device.core;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.device.model.UnifiedHealthRecord;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * OAuth 类型 Provider 的公共基类，统一处理「未配置凭证」时的降级行为。
 *
 * <p>子类需实现：
 * <ul>
 *   <li>{@link #providerName()} / {@link #displayName()} / {@link #deviceType()}</li>
 *   <li>{@link #isConfigured()}（检查 client-id/secret 是否非空）</li>
 *   <li>{@link #buildAuthorizeUrl(long, String)}（拼接厂商授权 URL）</li>
 *   <li>{@link #doExchangeCode(String, String)} / {@link #doRefreshToken(String)}</li>
 *   <li>{@link #doPullData(long, String, OAuthTokenExchange, LocalDate, LocalDate)}</li>
 *   <li>{@link #supportedMetrics()}</li>
 * </ul>
 *
 * <p>未配置时 OAuth 方法抛 503，前端展示「未配置」并隐藏「绑定」按钮。
 */
public abstract class AbstractOAuthProvider implements DeviceProvider {

  @Override
  public final String getAuthorizeUrl(long userId, String redirectUri) {
    ensureConfigured("getAuthorizeUrl");
    return buildAuthorizeUrl(userId, redirectUri);
  }

  @Override
  public final OAuthTokenExchange exchangeCode(String code, String redirectUri) {
    ensureConfigured("exchangeCode");
    return doExchangeCode(code, redirectUri);
  }

  @Override
  public final OAuthTokenExchange refreshToken(String refreshToken) {
    ensureConfigured("refreshToken");
    return doRefreshToken(refreshToken);
  }

  @Override
  public final List<UnifiedHealthRecord> pullData(
      long userId, String bindingExternalId, OAuthTokenExchange token, LocalDate from, LocalDate to
  ) {
    ensureConfigured("pullData");
    return doPullData(userId, bindingExternalId, token, from, to);
  }

  /** 拼接厂商授权 URL。 */
  protected abstract String buildAuthorizeUrl(long userId, String redirectUri);

  /** 用授权码换取 token。 */
  protected abstract OAuthTokenExchange doExchangeCode(String code, String redirectUri);

  /** 用 refresh_token 刷新 access_token。 */
  protected abstract OAuthTokenExchange doRefreshToken(String refreshToken);

  /** 拉取厂商数据。 */
  protected abstract List<UnifiedHealthRecord> doPullData(
      long userId, String bindingExternalId, OAuthTokenExchange token, LocalDate from, LocalDate to);

  private void ensureConfigured(String operation) {
    if (!isConfigured()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Provider [" + providerName() + "] 未配置凭证，无法执行 " + operation
              + "。请在后端 application.yml 或环境变量中配置 client-id/secret。"
      );
    }
  }
}
