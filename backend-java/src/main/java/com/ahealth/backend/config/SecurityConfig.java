package com.ahealth.backend.config;

import com.ahealth.backend.security.TokenAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      TokenAuthenticationFilter tokenAuthenticationFilter
  ) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/api/health",
                "/actuator/health",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/api/auth/login",
                "/api/auth/register",
                // 设备聚合平台：OAuth 回调需 permitAll（用户已登录态由 state 参数校验）
                "/api/devices/oauth/callback/**",
                // 开放 SDK API：使用 X-SDK-API-Key 鉴权，不走用户 token（Phase 7 SdkAuthFilter 处理）
                "/api/devices/sdk/reading",
                "/api/devices/sdk/readings/**",
                "/api/sdk/v1/**"
            ).permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
