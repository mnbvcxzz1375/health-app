package com.ahealth.backend.diet;

import com.ahealth.backend.common.JsonSupport;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 食材库查询：按关键词搜索 food_items 表。
 */
@Service
public class FoodService {
  private final JdbcTemplate jdbcTemplate;
  private final JsonSupport jsonSupport;

  public FoodService(JdbcTemplate jdbcTemplate, JsonSupport jsonSupport) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonSupport = jsonSupport;
  }

  public List<DietDtos.FoodSearchItem> searchFoods(String keyword, int limit) {
    String pattern = "%" + (keyword == null ? "" : keyword.trim()) + "%";
    int safeLimit = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 100));
    return jdbcTemplate.query(
        """
        SELECT id, name, category, calories_per_100g, protein_g, fat_g, carb_g, fiber_g,
               sodium_mg, potassium_mg, glycemic_index, tags
        FROM food_items
        WHERE name LIKE ? OR category LIKE ? OR tags LIKE ?
        ORDER BY id ASC
        LIMIT ?
        """,
        (rs, n) -> new DietDtos.FoodSearchItem(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getDouble("calories_per_100g"),
            rs.getDouble("protein_g"),
            rs.getDouble("fat_g"),
            rs.getDouble("carb_g"),
            rs.getDouble("fiber_g"),
            rs.getDouble("sodium_mg"),
            rs.getDouble("potassium_mg"),
            rs.getObject("glycemic_index", Integer.class),
            jsonSupport.readStringList(rs.getString("tags"))
        ),
        pattern, pattern, pattern, safeLimit
    );
  }
}
