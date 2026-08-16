package com.ahealth.backend.device.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ahealth.backend.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.RedisConnectionFailureException;

class OAuthStateServiceTest {

  @Test
  void stateBindsProviderAndUserAndIsSingleUse() {
    OAuthStateService service = new OAuthStateService("test-secret");

    String state = service.issue("fitbit", 42L);

    assertEquals(42L, service.consume("fitbit", state));
    assertThrows(ApiException.class, () -> service.consume("fitbit", state));
    assertThrows(ApiException.class, () -> service.consume("oura", service.issue("fitbit", 42L)));
  }

  @Test
  void authorizeUrlStateIsReplacedWithoutLeavingUserIdState() {
    OAuthStateService service = new OAuthStateService("test-secret");
    String state = service.issue("fitbit", 42L);

    String url = service.replaceState("https://provider.example/authorize?client_id=x&state=42-deprecated", state);

    org.junit.jupiter.api.Assertions.assertTrue(url.contains("state=" + java.net.URLEncoder.encode(state, java.nio.charset.StandardCharsets.UTF_8)));
    org.junit.jupiter.api.Assertions.assertFalse(url.contains("42-deprecated"));
  }

  @Test
  void redisOutageFallsBackOnlyToTheIssuingProcess() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    doThrow(new RedisConnectionFailureException("offline"))
        .when(values).set(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    doThrow(new RedisConnectionFailureException("offline"))
        .when(values).getAndDelete(org.mockito.ArgumentMatchers.anyString());

    OAuthStateService service = new OAuthStateService("test-secret", redis);
    String state = service.issue("fitbit", 42L);

    assertEquals(42L, service.consume("fitbit", state));
  }
}
