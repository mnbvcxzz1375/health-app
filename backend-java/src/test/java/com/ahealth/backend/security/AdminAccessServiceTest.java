package com.ahealth.backend.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import org.junit.jupiter.api.Test;

class AdminAccessServiceTest {
  @Test
  void configuredAdminUserIsAllowed() {
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(7L);
      new AdminAccessService("1,7").requireAdmin();
    }
  }

  @Test
  void nonAdminUserIsRejected() {
    try (var mocked = mockStatic(CurrentUser.class)) {
      mocked.when(CurrentUser::requireUserId).thenReturn(8L);
      assertThrows(ApiException.class, () -> new AdminAccessService("1,7").requireAdmin());
    }
  }
}
