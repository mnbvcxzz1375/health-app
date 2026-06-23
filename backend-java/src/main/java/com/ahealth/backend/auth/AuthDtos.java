package com.ahealth.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
  private AuthDtos() {}

  public record RegisterRequest(
      @NotBlank(message = "姓名不能为空") String name,
      @Email(message = "邮箱格式不正确") @NotBlank(message = "邮箱不能为空") String email,
      @Size(min = 6, message = "密码长度至少 6 位") String password
  ) {}

  public record LoginRequest(
      @Email(message = "邮箱格式不正确") @NotBlank(message = "邮箱不能为空") String email,
      @NotBlank(message = "密码不能为空") String password
  ) {}

  public record AuthUserView(
      String id,
      String name,
      String email,
      String avatarUrl
  ) {}

  public record AuthResponse(
      String token,
      AuthUserView user
  ) {}

  public record AuthSessionResponse(
      AuthUserView user
  ) {}
}
