package com.ahealth.backend.auth;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import com.ahealth.backend.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final AuthRepository authRepository;

  public AuthService(AuthRepository authRepository) {
    this.authRepository = authRepository;
  }

  @Transactional
  public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
    String email = normalizeEmail(request.email());
    authRepository.findUserByEmail(email).ifPresent(user -> {
      throw new ApiException(HttpStatus.CONFLICT, "该邮箱已注册，请直接登录");
    });

    long userId = authRepository.createUser(request.name().trim(), email, hashPassword(request.password()));
    authRepository.ensureProfile(userId, request.name().trim(), email);
    String token = generateToken();
    authRepository.createSession(token, userId);
    return new AuthDtos.AuthResponse(token, authRepository.findAuthUserView(userId).orElseThrow(
        () -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "用户信息读取失败")
    ));
  }

  @Transactional
  public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
    String email = normalizeEmail(request.email());
    AuthRepository.UserRow user = authRepository.findUserByEmail(email).orElseThrow(
        () -> new ApiException(HttpStatus.BAD_REQUEST, "邮箱或密码不正确")
    );

    if (!user.passwordHash().equals(hashPassword(request.password()))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "邮箱或密码不正确");
    }

    authRepository.ensureProfile(user.id(), user.name(), user.email());
    String token = generateToken();
    authRepository.createSession(token, user.id());
    return new AuthDtos.AuthResponse(token, authRepository.findAuthUserView(user.id()).orElseThrow(
        () -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "用户信息读取失败")
    ));
  }

  public AuthDtos.AuthSessionResponse me() {
    AuthenticatedUser user = CurrentUser.require();
    return new AuthDtos.AuthSessionResponse(authRepository.findAuthUserView(user.userId()).orElseThrow(
        () -> new ApiException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录")
    ));
  }

  public void logout() {
    AuthenticatedUser user = CurrentUser.require();
    authRepository.deleteSession(user.token());
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }

  private String generateToken() {
    return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }

  private String hashPassword(String password) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 不可用", exception);
    }
  }
}
