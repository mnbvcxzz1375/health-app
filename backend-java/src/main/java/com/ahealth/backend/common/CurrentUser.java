package com.ahealth.backend.common;

import com.ahealth.backend.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
  private CurrentUser() {}

  public static AuthenticatedUser require() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
    }
    return user;
  }

  public static long requireUserId() {
    return require().userId();
  }
}
