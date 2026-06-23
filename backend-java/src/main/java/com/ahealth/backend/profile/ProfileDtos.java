package com.ahealth.backend.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class ProfileDtos {
  private ProfileDtos() {}

  public record ProfileSummaryResponse(
      String devices,
      String uploads,
      String riskScore
  ) {}

  public record ProfileSettingsResponse(
      String name,
      String email,
      int age,
      String gender,
      int height,
      int weight,
      String focus,
      List<String> goals,
      boolean dailySummary,
      boolean riskAlert,
      boolean rehabReminder
  ) {}

  public record SaveProfileSettingsRequest(
      @NotBlank(message = "姓名不能为空") String name,
      @Email(message = "邮箱格式不正确") @NotBlank(message = "邮箱不能为空") String email,
      @NotNull(message = "年龄不能为空") Integer age,
      @NotBlank(message = "性别不能为空") String gender,
      @NotNull(message = "身高不能为空") Integer height,
      @NotNull(message = "体重不能为空") Integer weight,
      String focus,
      List<String> goals,
      Boolean dailySummary,
      Boolean riskAlert,
      Boolean rehabReminder
  ) {}

  public record AvatarRequest(String avatarUrl) {}
}
