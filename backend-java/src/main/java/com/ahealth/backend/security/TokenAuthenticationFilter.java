package com.ahealth.backend.security;

import com.ahealth.backend.auth.AuthRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
  private final AuthRepository authRepository;

  public TokenAuthenticationFilter(AuthRepository authRepository) {
    this.authRepository = authRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7).trim();
      authRepository.findSessionUser(token).ifPresent(user -> {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            user,
            token,
            AuthorityUtils.NO_AUTHORITIES
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        authRepository.touchSession(token);
      });
    }

    filterChain.doFilter(request, response);
  }
}
