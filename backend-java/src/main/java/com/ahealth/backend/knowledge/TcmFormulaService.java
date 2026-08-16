package com.ahealth.backend.knowledge;

import com.ahealth.backend.common.ApiException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 中药方剂 CRUD。
 * 操作 tcm_formulas 与 formula_herbs 两张表。
 */
@Service
public class TcmFormulaService {
  private final JdbcTemplate jdbcTemplate;
  private final DrugKnowledgeService drugKnowledgeService;

  public TcmFormulaService(JdbcTemplate jdbcTemplate, DrugKnowledgeService drugKnowledgeService) {
    this.jdbcTemplate = jdbcTemplate;
    this.drugKnowledgeService = drugKnowledgeService;
  }

  @Transactional
  public KnowledgeDtos.FormulaResponse createFormula(long userId, KnowledgeDtos.FormulaSaveRequest req) {
    if (req == null || req.name() == null || req.name().trim().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "方剂名称不能为空");
    }
    if (req.herbs() == null || req.herbs().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "方剂至少需要一味药材");
    }

    jdbcTemplate.update(
        """
        INSERT INTO tcm_formulas (user_id, name, diagnosis, prescribed_at, notes, created_at)
        VALUES (?, ?, ?, CURDATE(), ?, NOW())
        """,
        userId,
        req.name().trim(),
        req.diagnosis() == null ? "" : req.diagnosis().trim(),
        req.notes() == null ? "" : req.notes().trim()
    );
    Long formulaId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    long fid = formulaId == null ? 0L : formulaId;

    for (KnowledgeDtos.FormulaHerbInput herb : req.herbs()) {
      if (herb == null || herb.herbName() == null || herb.herbName().trim().isBlank()) {
        continue;
      }
      String herbName = herb.herbName().trim();
      Long herbId = resolveHerbId(herbName);
      jdbcTemplate.update(
          """
          INSERT INTO formula_herbs (formula_id, herb_id, herb_name, grams, role)
          VALUES (?, ?, ?, ?, ?)
          """,
          fid,
          herbId,
          herbName,
          herb.dosageGrams() == null ? null : herb.dosageGrams(),
          herb.role() == null ? "" : herb.role().trim()
      );
    }
    return getFormula(fid);
  }

  public KnowledgeDtos.FormulaResponse getFormula(long id) {
    try {
      Map<String, Object> row = jdbcTemplate.queryForMap(
          """
          SELECT id, user_id, name, diagnosis, notes, created_at
          FROM tcm_formulas
          WHERE id = ?
          """,
          id
      );
      List<KnowledgeDtos.FormulaHerbItem> herbs = jdbcTemplate.query(
          """
          SELECT fh.herb_id, fh.herb_name, fh.grams, fh.role,
                 h.pinyin, h.nature, h.flavor, h.meridian, h.efficacy
          FROM formula_herbs fh
          LEFT JOIN tcm_herbs h ON h.id = fh.herb_id
          WHERE fh.formula_id = ?
          ORDER BY fh.id ASC
          """,
          (rs, n) -> new KnowledgeDtos.FormulaHerbItem(
              rs.getLong("herb_id"),
              rs.getString("herb_name"),
              rs.getString("pinyin") == null ? "" : rs.getString("pinyin"),
              rs.getString("nature") == null ? "" : rs.getString("nature"),
              rs.getString("flavor") == null ? "" : rs.getString("flavor"),
              rs.getString("meridian") == null ? "" : rs.getString("meridian"),
              rs.getString("efficacy") == null ? "" : rs.getString("efficacy"),
              rs.getObject("grams", Integer.class) == null
                  ? null
                  : ((java.math.BigDecimal) rs.getObject("grams")).intValue(),
              rs.getString("role") == null ? "" : rs.getString("role")
          ),
          id
      );
      return new KnowledgeDtos.FormulaResponse(
          longValue(row.get("id")),
          stringValue(row.get("name")),
          stringValue(row.get("diagnosis")),
          herbs,
          stringValue(row.get("notes")),
          (LocalDateTime) row.get("created_at")
      );
    } catch (EmptyResultDataAccessException e) {
      throw new ApiException(HttpStatus.NOT_FOUND, "方剂不存在");
    }
  }

  public List<KnowledgeDtos.FormulaListItem> listFormulas(long userId) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        """
        SELECT f.id, f.name, f.created_at,
               (SELECT COUNT(*) FROM formula_herbs fh WHERE fh.formula_id = f.id) AS herb_count
        FROM tcm_formulas f
        WHERE f.user_id = ? OR f.user_id IS NULL
        ORDER BY f.created_at DESC, f.id DESC
        """,
        userId
    );
    List<KnowledgeDtos.FormulaListItem> result = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      result.add(new KnowledgeDtos.FormulaListItem(
          longValue(row.get("id")),
          stringValue(row.get("name")),
          intValue(row.get("herb_count"), 0),
          (LocalDateTime) row.get("created_at")
      ));
    }
    return result;
  }

  @Transactional
  public boolean deleteFormula(long id, long userId) {
    // 校验 owner（user_id IS NULL 的为系统预置，不允许删除）
    try {
      Map<String, Object> row = jdbcTemplate.queryForMap(
          "SELECT user_id FROM tcm_formulas WHERE id = ?",
          id
      );
      Object ownerObj = row.get("user_id");
      long owner = ownerObj instanceof Number n ? n.longValue() : 0L;
      if (owner != userId) {
        throw new ApiException(HttpStatus.FORBIDDEN, "无权删除该方剂");
      }
    } catch (EmptyResultDataAccessException e) {
      throw new ApiException(HttpStatus.NOT_FOUND, "方剂不存在");
    }
    jdbcTemplate.update("DELETE FROM formula_herbs WHERE formula_id = ?", id);
    int deleted = jdbcTemplate.update("DELETE FROM tcm_formulas WHERE id = ?", id);
    return deleted > 0;
  }

  /** 取方剂中所有药材名（供交互检查 / 饮食忌口使用）。 */
  public List<String> getFormulaHerbNames(long formulaId) {
    return jdbcTemplate.queryForList(
        "SELECT herb_name FROM formula_herbs WHERE formula_id = ?",
        String.class,
        formulaId
    );
  }

  private Long resolveHerbId(String herbName) {
    return drugKnowledgeService.getHerbByName(herbName)
        .map(KnowledgeDtos.HerbSearchItem::id)
        .orElse(null);
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private long longValue(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private int intValue(Object value, int fallback) {
    return value instanceof Number number ? number.intValue() : fallback;
  }
}
