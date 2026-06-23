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
                "/api/auth/register"
            ).permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
