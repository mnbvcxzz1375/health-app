package com.ahealth.backend.profile;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
  private final ProfileService profileService;

  public ProfileController(ProfileService profileService) {
    this.profileService = profileService;
  }

  @GetMapping("/summary")
  public ProfileDtos.ProfileSummaryResponse summary() {
    return profileService.getSummary();
  }

  @GetMapping("/settings")
  public ProfileDtos.ProfileSettingsResponse settings() {
    return profileService.getSettings();
  }

  @PostMapping("/settings")
  public ProfileDtos.ProfileSettingsResponse save(@Valid @RequestBody ProfileDtos.SaveProfileSettingsRequest request) {
    return profileService.saveSettings(request);
  }

  @PostMapping("/avatar")
  public Map<String, Boolean> avatar(@RequestBody ProfileDtos.AvatarRequest request) {
    profileService.updateAvatar(request);
    return Map.of("success", true);
  }
}
