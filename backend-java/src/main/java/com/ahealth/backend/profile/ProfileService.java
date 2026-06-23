package com.ahealth.backend.profile;

import com.ahealth.backend.common.CurrentUser;
import com.ahealth.backend.common.JsonSupport;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
  private final JdbcTemplate jdbcTemplate;
  private final JsonSupport jsonSupport;

  public ProfileService(JdbcTemplate jdbcTemplate, JsonSupport jsonSupport) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonSupport = jsonSupport;
  }

  public ProfileDtos.ProfileSummaryResponse getSummary() {
    long userId = CurrentUser.requireUserId();
    Map<String, Object> profile = jdbcTemplate.queryForMap(
        "SELECT risk_score, risk_level FROM user_profiles WHERE id = ?",
        userId
    );
    Integer deviceCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM devices WHERE user_id = ?",
        Integer.class,
        userId
    );
    Integer uploadCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM analyze_tasks WHERE user_id = ?",
        Integer.class,
        userId
    );
    return new ProfileDtos.ProfileSummaryResponse(
        "%d 台（设备）".formatted(deviceCount == null ? 0 : deviceCount),
        "%d 份".formatted(uploadCount == null ? 0 : uploadCount),
        "%s · %s".formatted(profile.get("risk_score"), profile.get("risk_level"))
    );
  }

  public ProfileDtos.ProfileSettingsResponse getSettings() {
    long userId = CurrentUser.requireUserId();
    Map<String, Object> row = jdbcTemplate.queryForMap(
        """
        SELECT up.name, up.email, us.age, us.gender, us.height, us.weight, us.focus,
               us.goals_json, us.daily_summary, us.risk_alert, us.rehab_reminder
        FROM user_profiles up
        JOIN user_settings us ON us.user_id = up.id
        WHERE up.id = ?
        """,
        userId
    );

    return new ProfileDtos.ProfileSettingsResponse(
        String.valueOf(row.get("name")),
        String.valueOf(row.get("email")),
        number(row.get("age")),
        String.valueOf(row.get("gender")),
        number(row.get("height")),
        number(row.get("weight")),
        stringValue(row.get("focus")),
        jsonSupport.readStringList(stringValue(row.get("goals_json"))),
        boolValue(row.get("daily_summary")),
        boolValue(row.get("risk_alert")),
        boolValue(row.get("rehab_reminder"))
    );
  }

  @Transactional
  public ProfileDtos.ProfileSettingsResponse saveSettings(ProfileDtos.SaveProfileSettingsRequest request) {
    long userId = CurrentUser.requireUserId();
    List<String> goals = request.goals() == null ? List.of() : request.goals();
    String gender = switch (request.gender()) {
      case "male", "female", "other" -> request.gender();
      default -> "other";
    };

    jdbcTemplate.update("UPDATE user_profiles SET name = ?, email = ? WHERE id = ?",
        request.name().trim(),
        request.email().trim().toLowerCase(),
        userId
    );
    jdbcTemplate.update(
        """
        UPDATE user_settings
        SET age = ?, gender = ?, height = ?, weight = ?, focus = ?, goals_json = ?,
            daily_summary = ?, risk_alert = ?, rehab_reminder = ?
        WHERE user_id = ?
        """,
        request.age(),
        gender,
        request.height(),
        request.weight(),
        request.focus() == null ? "" : request.focus().trim(),
        jsonSupport.write(goals),
        truthy(request.dailySummary()),
        truthy(request.riskAlert()),
        truthy(request.rehabReminder()),
        userId
    );
    jdbcTemplate.update("UPDATE auth_users SET name = ?, email = ? WHERE id = ?",
        request.name().trim(),
        request.email().trim().toLowerCase(),
        userId
    );
    return new ProfileDtos.ProfileSettingsResponse(
        request.name().trim(),
        request.email().trim().toLowerCase(),
        request.age(),
        gender,
        request.height(),
        request.weight(),
        request.focus() == null ? "" : request.focus().trim(),
        goals,
        Boolean.TRUE.equals(request.dailySummary()),
        Boolean.TRUE.equals(request.riskAlert()),
        Boolean.TRUE.equals(request.rehabReminder())
    );
  }

  public void updateAvatar(ProfileDtos.AvatarRequest request) {
    long userId = CurrentUser.requireUserId();
    jdbcTemplate.update("UPDATE user_profiles SET avatar_url = ? WHERE id = ?", request.avatarUrl(), userId);
  }

  private int number(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private boolean boolValue(Object value) {
    return value instanceof Number number && number.intValue() == 1;
  }

  private int truthy(Boolean value) {
    return Boolean.TRUE.equals(value) ? 1 : 0;
  }
}
