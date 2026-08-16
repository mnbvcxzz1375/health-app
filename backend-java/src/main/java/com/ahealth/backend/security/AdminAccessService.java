package com.ahealth.backend.security;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Central authorization guard for every /api/admin endpoint. */
@Service
public class AdminAccessService {
  private final Set<Long> adminUserIds;

  public AdminAccessService(@Value("${custom.admin.user-ids:1}") String rawUserIds) {
    this.adminUserIds = parse(rawUserIds);
  }

  public void requireAdmin() {
    long userId = CurrentUser.requireUserId();
    if (!adminUserIds.contains(userId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "仅管理员可执行此操作");
    }
  }

  private static Set<Long> parse(String raw) {
    if (raw == null || raw.isBlank()) return Set.of();
    return Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(value -> {
          try {
            return Long.valueOf(value);
          } catch (NumberFormatException ignored) {
            return null;
          }
        })
        .filter(value -> value != null)
        .collect(Collectors.toUnmodifiableSet());
  }
}
