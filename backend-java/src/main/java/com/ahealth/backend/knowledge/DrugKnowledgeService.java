package com.ahealth.backend.knowledge;

import com.ahealth.backend.common.JsonSupport;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 药品临床信息查询 + 中药材搜索。
 * 直查 drug_clinical_info 与 tcm_herbs 两张表。
 */
@Service
public class DrugKnowledgeService {
  private final JdbcTemplate jdbcTemplate;
  private final JsonSupport jsonSupport;

  public DrugKnowledgeService(JdbcTemplate jdbcTemplate, JsonSupport jsonSupport) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonSupport = jsonSupport;
  }

  public KnowledgeDtos.ClinicalInfoResponse getClinicalInfo(String drugName) {
    if (drugName == null || drugName.trim().isBlank()) {
      return null;
    }
    try {
      var row = jdbcTemplate.queryForMap(
          """
          SELECT drug_name, medicine_type, ingredients, indications, side_effects,
                 allergic_reactions, contraindicated_groups, contraindications,
                 interactions, dietary_taboos, dosing_interval_minutes, source
          FROM drug_clinical_info
          WHERE drug_name = ?
          LIMIT 1
          """,
          drugName.trim()
      );
      return new KnowledgeDtos.ClinicalInfoResponse(
          stringValue(row.get("drug_name")),
          stringValue(row.get("medicine_type")),
          jsonSupport.readStringList(stringValue(row.get("ingredients"))),
          stringValue(row.get("indications")),
          jsonSupport.readStringList(stringValue(row.get("side_effects"))),
          jsonSupport.readStringList(stringValue(row.get("allergic_reactions"))),
          jsonSupport.readStringList(stringValue(row.get("contraindicated_groups"))),
          stringValue(row.get("contraindications")),
          jsonSupport.readStringList(stringValue(row.get("interactions"))),
          jsonSupport.readStringList(stringValue(row.get("dietary_taboos"))),
          intValue(row.get("dosing_interval_minutes")),
          stringValue(row.get("source"))
      );
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  public List<KnowledgeDtos.HerbSearchItem> searchHerbs(String keyword, int limit) {
    if (keyword == null || keyword.trim().isBlank()) {
      return List.of();
    }
    String pattern = "%" + keyword.trim() + "%";
    int safeLimit = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 100));
    return jdbcTemplate.query(
        """
        SELECT id, name, pinyin, alias, nature, flavor, meridian, efficacy
        FROM tcm_herbs
        WHERE name LIKE ? OR pinyin LIKE ? OR alias LIKE ?
        ORDER BY id ASC
        LIMIT ?
        """,
        (rs, n) -> new KnowledgeDtos.HerbSearchItem(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("pinyin"),
            rs.getString("alias"),
            rs.getString("nature"),
            rs.getString("flavor"),
            rs.getString("meridian"),
            rs.getString("efficacy")
        ),
        pattern, pattern, pattern, safeLimit
    );
  }

  public Optional<KnowledgeDtos.HerbSearchItem> getHerbByName(String name) {
    if (name == null || name.trim().isBlank()) {
      return Optional.empty();
    }
    try {
      var row = jdbcTemplate.queryForMap(
          """
          SELECT id, name, pinyin, alias, nature, flavor, meridian, efficacy
          FROM tcm_herbs
          WHERE name = ?
          LIMIT 1
          """,
          name.trim()
      );
      return Optional.of(new KnowledgeDtos.HerbSearchItem(
          longValue(row.get("id")),
          stringValue(row.get("name")),
          stringValue(row.get("pinyin")),
          stringValue(row.get("alias")),
          stringValue(row.get("nature")),
          stringValue(row.get("flavor")),
          stringValue(row.get("meridian")),
          stringValue(row.get("efficacy"))
      ));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  /** 根据 herb_name 列表批量查询，返回 name -> HerbSearchItem 映射。 */
  public java.util.Map<String, KnowledgeDtos.HerbSearchItem> getHerbsByNames(List<String> names) {
    if (names == null || names.isEmpty()) {
      return java.util.Map.of();
    }
    java.util.Map<String, KnowledgeDtos.HerbSearchItem> result = new java.util.HashMap<>();
    for (String name : names) {
      getHerbByName(name).ifPresent(item -> result.put(item.name(), item));
    }
    return result;
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private long longValue(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private Integer intValue(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }
}
