package com.ahealth.backend.diet;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.JsonSupport;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户饮食偏好管理（UPSERT user_diet_preferences 表）。
 */
@Service
public class DietPreferenceService {
  private final JdbcTemplate jdbcTemplate;
  private final JsonSupport jsonSupport;

  public DietPreferenceService(JdbcTemplate jdbcTemplate, JsonSupport jsonSupport) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonSupport = jsonSupport;
  }

  public DietDtos.DietPreference getPreference(long userId) {
    try {
      var row = jdbcTemplate.queryForMap(
          """
          SELECT diet_style, disliked_foods, preferred_cuisine, daily_meal_count,
                 avoid_spicy, avoid_cold, vegetarian, updated_at
          FROM user_diet_preferences
          WHERE user_id = ?
          """,
          userId
      );
      return new DietDtos.DietPreference(
          stringValue(row.get("diet_style")),
          jsonSupport.readStringList(stringValue(row.get("disliked_foods"))),
          stringValue(row.get("preferred_cuisine")),
          intValue(row.get("daily_meal_count"), 3),
          boolValue(row.get("avoid_spicy")),
          boolValue(row.get("avoid_cold")),
          boolValue(row.get("vegetarian")),
          row.get("updated_at") instanceof java.sql.Date d ? d.toLocalDate()
              : (row.get("updated_at") instanceof java.sql.Timestamp ts ? ts.toLocalDateTime().toLocalDate() : LocalDate.now())
      );
    } catch (EmptyResultDataAccessException e) {
      return new DietDtos.DietPreference("balanced", List.of(), "", 3, false, false, false, LocalDate.now());
    }
  }

  @Transactional
  public DietDtos.DietPreference savePreference(long userId, DietDtos.DietPreferenceSaveRequest req) {
    if (req == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "饮食偏好参数不能为空");
    }
    String style = (req.dietStyle() == null || req.dietStyle().isBlank()) ? "balanced" : req.dietStyle().trim();
    int meals = req.dailyMealCount() == null || req.dailyMealCount() < 1 ? 3 : Math.min(req.dailyMealCount(), 6);
    List<String> disliked = req.dislikedFoods() == null ? List.of() : req.dislikedFoods();
    String cuisine = req.preferredCuisine() == null ? "" : req.preferredCuisine().trim();
    boolean avoidSpicy = Boolean.TRUE.equals(req.avoidSpicy());
    boolean avoidCold = Boolean.TRUE.equals(req.avoidCold());
    boolean vegetarian = Boolean.TRUE.equals(req.vegetarian());

    jdbcTemplate.update(
        """
        INSERT INTO user_diet_preferences (
          user_id, diet_style, disliked_foods, preferred_cuisine, daily_meal_count,
          avoid_spicy, avoid_cold, vegetarian
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          diet_style = VALUES(diet_style),
          disliked_foods = VALUES(disliked_foods),
          preferred_cuisine = VALUES(preferred_cuisine),
          daily_meal_count = VALUES(daily_meal_count),
          avoid_spicy = VALUES(avoid_spicy),
          avoid_cold = VALUES(avoid_cold),
          vegetarian = VALUES(vegetarian)
        """,
        userId, style, jsonSupport.write(disliked), cuisine, meals,
        avoidSpicy ? 1 : 0, avoidCold ? 1 : 0, vegetarian ? 1 : 0
    );
    return getPreference(userId);
  }

  private String stringValue(Object v) { return v == null ? "" : String.valueOf(v); }
  private int intValue(Object v, int def) {
    if (v == null) return def;
    if (v instanceof Number n) return n.intValue();
    try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return def; }
  }

  private boolean boolValue(Object v) {
    if (v instanceof Boolean b) return b;
    if (v instanceof Number n) return n.intValue() != 0;
    return v != null && ("1".equals(v.toString()) || "true".equalsIgnoreCase(v.toString()));
  }
}
