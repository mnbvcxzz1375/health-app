package com.ahealth.backend.knowledge;

import com.ahealth.backend.common.CurrentUser;
import com.ahealth.backend.common.JsonSupport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 综合交互检查（核心安全能力）。
 *
 * <p>对用户当前用药清单查询 4 张交互表 + 1 张禁忌人群表 + 用户过敏表，
 * 汇总为 6 类告警报告：十八反十九畏 / 中西药交互 / 药食相互作用 / DDI 警告 / 过敏冲突 / 禁忌人群警告。
 *
 * <p>DDI 直查 ddi_knowledge 表，避免与 MedicationService 形成循环依赖。
 */
@Service
public class InteractionCheckService {
  private final JdbcTemplate jdbcTemplate;
  private final JsonSupport jsonSupport;
  private final DrugKnowledgeService drugKnowledgeService;
  private final TcmFormulaService tcmFormulaService;

  public InteractionCheckService(
      JdbcTemplate jdbcTemplate,
      JsonSupport jsonSupport,
      DrugKnowledgeService drugKnowledgeService,
      TcmFormulaService tcmFormulaService
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonSupport = jsonSupport;
    this.drugKnowledgeService = drugKnowledgeService;
    this.tcmFormulaService = tcmFormulaService;
  }

  /** 综合交互检查主入口。 */
  public KnowledgeDtos.InteractionReport checkAll() {
    long userId = CurrentUser.requireUserId();
    return checkAllForUser(userId);
  }

  /** 按指定 userId 执行交互检查（供其他 service 复用）。 */
  public KnowledgeDtos.InteractionReport checkAllForUser(long userId) {
    // 1. 取用户当日启用药物（含 medicine_type 与 formula_id）
    List<MedicationBrief> medications = listUserMedications(userId);
    List<String> westernDrugs = new ArrayList<>();
    List<String> herbNames = new ArrayList<>();
    for (MedicationBrief med : medications) {
      if ("formula".equalsIgnoreCase(med.medicineType()) && med.formulaId() != null) {
        herbNames.addAll(tcmFormulaService.getFormulaHerbNames(med.formulaId()));
      } else if ("tcm".equalsIgnoreCase(med.medicineType())) {
        herbNames.add(med.name());
      } else {
        westernDrugs.add(med.name());
      }
    }
    // 去重
    List<String> herbs = herbNames.stream().distinct().toList();
    List<String> westerns = westernDrugs.stream().distinct().toList();

    // 2. 六步检查
    List<KnowledgeDtos.InteractionRecord> tcmIncompat = checkTcmIncompatibility(herbs);
    List<KnowledgeDtos.InteractionRecord> tcmWm = checkTcmWmInteraction(herbs, westerns);
    List<KnowledgeDtos.InteractionRecord> drugFood = checkDrugFoodInteraction(herbs, westerns);
    List<KnowledgeDtos.InteractionRecord> ddi = checkDdi(westerns);
    List<KnowledgeDtos.InteractionRecord> allergyConflicts = checkAllergyConflicts(userId, herbs, westerns);
    List<KnowledgeDtos.InteractionRecord> contraindicated = checkContraindicatedGroups(userId, herbs, westerns);

    int total = tcmIncompat.size() + tcmWm.size() + drugFood.size()
        + ddi.size() + allergyConflicts.size() + contraindicated.size();

    List<String> summary = new ArrayList<>();
    if (!tcmIncompat.isEmpty()) {
      summary.add("发现 " + tcmIncompat.size() + " 条十八反十九畏告警，请核对处方");
    }
    if (!tcmWm.isEmpty()) {
      summary.add("发现 " + tcmWm.size() + " 条中西药交互风险，建议间隔服用");
    }
    if (!drugFood.isEmpty()) {
      summary.add("发现 " + drugFood.size() + " 条药食相互作用，注意饮食禁忌");
    }
    if (!ddi.isEmpty()) {
      summary.add("发现 " + ddi.size() + " 条西药 DDI 警告");
    }
    if (!allergyConflicts.isEmpty()) {
      summary.add("发现 " + allergyConflicts.size() + " 条过敏冲突，请立即停药并咨询医生");
    }
    if (!contraindicated.isEmpty()) {
      summary.add("发现 " + contraindicated.size() + " 条禁忌人群警告");
    }
    if (summary.isEmpty()) {
      summary.add("当前用药清单未发现交互风险");
    }

    return new KnowledgeDtos.InteractionReport(
        tcmIncompat, tcmWm, drugFood, ddi, allergyConflicts, contraindicated,
        total, summary
    );
  }

  // ===== 过敏管理 =====

  public List<KnowledgeDtos.AllergyItem> getUserAllergies() {
    long userId = CurrentUser.requireUserId();
    return getUserAllergies(userId);
  }

  public List<KnowledgeDtos.AllergyItem> getUserAllergies(long userId) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        """
        SELECT id, allergen, allergen_type, reaction, severity
        FROM user_allergies
        WHERE user_id = ?
        ORDER BY id DESC
        """,
        userId
    );
    List<KnowledgeDtos.AllergyItem> result = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      result.add(new KnowledgeDtos.AllergyItem(
          longValue(row.get("id")),
          stringValue(row.get("allergen")),
          stringValue(row.get("allergen_type")),
          stringValue(row.get("severity")),
          stringValue(row.get("reaction"))
      ));
    }
    return result;
  }

  @Transactional
  public KnowledgeDtos.AllergyItem addAllergy(KnowledgeDtos.AllergySaveRequest req) {
    long userId = CurrentUser.requireUserId();
    if (req == null || req.allergen() == null || req.allergen().trim().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "过敏原不能为空");
    }
    String allergen = req.allergen().trim();
    String type = req.allergenType() == null ? "food" : req.allergenType().trim();
    String severity = req.severity() == null ? "moderate" : req.severity().trim();
    String note = req.note() == null ? "" : req.note().trim();

    jdbcTemplate.update(
        """
        INSERT INTO user_allergies (user_id, allergen, allergen_type, reaction, severity)
        VALUES (?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          allergen_type = VALUES(allergen_type),
          reaction = VALUES(reaction),
          severity = VALUES(severity)
        """,
        userId, allergen, type, note, severity
    );
    Long id = jdbcTemplate.queryForObject(
        "SELECT id FROM user_allergies WHERE user_id = ? AND allergen = ?",
        Long.class, userId, allergen
    );
    return new KnowledgeDtos.AllergyItem(id == null ? 0L : id, allergen, type, severity, note);
  }

  @Transactional
  public boolean removeAllergy(long id) {
    long userId = CurrentUser.requireUserId();
    int deleted = jdbcTemplate.update(
        "DELETE FROM user_allergies WHERE id = ? AND user_id = ?",
        id, userId
    );
    return deleted > 0;
  }

  // ===== 私有检查方法 =====

  /** 1. 十八反十九畏：药材两两 JOIN tcm_incompatibility。 */
  private List<KnowledgeDtos.InteractionRecord> checkTcmIncompatibility(List<String> herbs) {
    if (herbs.size() < 2) return List.of();
    List<KnowledgeDtos.InteractionRecord> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < herbs.size(); i++) {
      for (int j = i + 1; j < herbs.size(); j++) {
        String a = herbs.get(i);
        String b = herbs.get(j);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT type, description, source FROM tcm_incompatibility
            WHERE (herb_a = ? AND herb_b = ?) OR (herb_a = ? AND herb_b = ?)
            """,
            a, b, b, a
        );
        for (Map<String, Object> row : rows) {
          String key = a + "|" + b + "|" + stringValue(row.get("type"));
          if (seen.add(key)) {
            result.add(new KnowledgeDtos.InteractionRecord(
                "tcm_incompatibility",
                "high",
                a, b,
                stringValue(row.get("description")),
                stringValue(row.get("source"))
            ));
          }
        }
      }
    }
    return result;
  }

  /** 2. 中西药交互：对每个西药 × 每个中药查 tcm_wm_interaction。 */
  private List<KnowledgeDtos.InteractionRecord> checkTcmWmInteraction(List<String> herbs, List<String> westerns) {
    if (herbs.isEmpty() || westerns.isEmpty()) return List.of();
    List<KnowledgeDtos.InteractionRecord> result = new ArrayList<>();
    for (String wm : westerns) {
      for (String tcm : herbs) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT severity, interaction_type, recommended_interval_minutes, description, evidence_source
            FROM tcm_wm_interaction
            WHERE tcm_name = ? AND wm_name = ?
            """,
            tcm, wm
        );
        for (Map<String, Object> row : rows) {
          result.add(new KnowledgeDtos.InteractionRecord(
              "tcm_wm_interaction",
              stringValue(row.get("severity")),
              tcm, wm,
              stringValue(row.get("description")),
              stringValue(row.get("evidence_source"))
          ));
        }
      }
    }
    return result;
  }

  /** 3. 药食相互作用：对每个药名查 drug_food_interaction。 */
  private List<KnowledgeDtos.InteractionRecord> checkDrugFoodInteraction(List<String> herbs, List<String> westerns) {
    List<KnowledgeDtos.InteractionRecord> result = new ArrayList<>();
    Set<String> allDrugs = new HashSet<>();
    allDrugs.addAll(herbs);
    allDrugs.addAll(westerns);
    for (String drug : allDrugs) {
      List<Map<String, Object>> rows = jdbcTemplate.queryForList(
          """
          SELECT food_category, food_items, severity, description, source
          FROM drug_food_interaction
          WHERE drug_name = ?
          """,
          drug
      );
      for (Map<String, Object> row : rows) {
        String foodItems = stringValue(row.get("food_items"));
        result.add(new KnowledgeDtos.InteractionRecord(
            "drug_food_interaction",
            stringValue(row.get("severity")),
            drug,
            stringValue(row.get("food_category")),
            stringValue(row.get("description")) + (foodItems.isBlank() ? "" : "（涉及食物：" + foodItems + "）"),
            stringValue(row.get("source"))
        ));
      }
    }
    return result;
  }

  /** 4. DDI 警告：对西药两两查 ddi_knowledge。 */
  private List<KnowledgeDtos.InteractionRecord> checkDdi(List<String> westerns) {
    if (westerns.size() < 2) return List.of();
    List<KnowledgeDtos.InteractionRecord> result = new ArrayList<>();
    for (int i = 0; i < westerns.size(); i++) {
      for (int j = i + 1; j < westerns.size(); j++) {
        String a = westerns.get(i);
        String b = westerns.get(j);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT severity, description, recommendation, source FROM ddi_knowledge
            WHERE (drug_a = ? AND drug_b = ?) OR (drug_a = ? AND drug_b = ?)
            """,
            a, b, b, a
        );
        for (Map<String, Object> row : rows) {
          String desc = stringValue(row.get("description"));
          String rec = stringValue(row.get("recommendation"));
          result.add(new KnowledgeDtos.InteractionRecord(
              "ddi",
              stringValue(row.get("severity")),
              a, b,
              desc + (rec.isBlank() ? "" : " 建议：" + rec),
              stringValue(row.get("source"))
          ));
        }
      }
    }
    return result;
  }

  /** 5. 过敏冲突：用户过敏原 vs 药物 ingredients 关键词匹配。 */
  private List<KnowledgeDtos.InteractionRecord> checkAllergyConflicts(
      long userId, List<String> herbs, List<String> westerns
  ) {
    List<KnowledgeDtos.AllergyItem> allergies = getUserAllergies(userId);
    if (allergies.isEmpty()) return List.of();
    List<KnowledgeDtos.InteractionRecord> result = new ArrayList<>();
    Set<String> allDrugs = new HashSet<>();
    allDrugs.addAll(herbs);
    allDrugs.addAll(westerns);
    for (String drug : allDrugs) {
      KnowledgeDtos.ClinicalInfoResponse clinical = drugKnowledgeService.getClinicalInfo(drug);
      if (clinical == null) continue;
      String ingredients = String.join(",", clinical.ingredients()).toLowerCase();
      if (ingredients.isBlank()) continue;
      for (KnowledgeDtos.AllergyItem allergy : allergies) {
        String allergen = allergy.allergen().toLowerCase();
        if (allergen.isBlank()) continue;
        if (ingredients.contains(allergen) || drug.toLowerCase().contains(allergen)) {
          result.add(new KnowledgeDtos.InteractionRecord(
              "allergy_conflict",
              "high".equalsIgnoreCase(allergy.severity()) ? "high" : "moderate",
              drug, allergy.allergen(),
              "药物「" + drug + "」含过敏原「" + allergy.allergen() + "」，" +
                  (allergy.note().isBlank() ? "" : "既往反应：" + allergy.note() + "；") +
                  "建议避免使用",
              "用户过敏史"
          ));
        }
      }
    }
    return result;
  }

  /** 6. 禁忌人群：drug_clinical_info.contraindicated_groups vs 用户 profile。 */
  private List<KnowledgeDtos.InteractionRecord> checkContraindicatedGroups(
      long userId, List<String> herbs, List<String> westerns
  ) {
    UserProfileBrief profile = fetchUserProfileBrief(userId);
    if (profile == null) return List.of();
    List<KnowledgeDtos.InteractionRecord> result = new ArrayList<>();
    Set<String> allDrugs = new HashSet<>();
    allDrugs.addAll(herbs);
    allDrugs.addAll(westerns);
    for (String drug : allDrugs) {
      KnowledgeDtos.ClinicalInfoResponse clinical = drugKnowledgeService.getClinicalInfo(drug);
      if (clinical == null) continue;
      for (String group : clinical.contraindicatedGroups()) {
        if (group == null || group.isBlank()) continue;
        String g = group.toLowerCase();
        boolean matched = false;
        String reason = "";
        if (g.contains("孕") && "pregnant".equalsIgnoreCase(profile.specialState())) {
          matched = true;
          reason = "用户处于孕期，该药物禁用";
        } else if (g.contains("哺乳") && "lactating".equalsIgnoreCase(profile.specialState())) {
          matched = true;
          reason = "用户处于哺乳期，该药物禁用";
        } else if (g.contains("儿童") && profile.age() != null && profile.age() < 14) {
          matched = true;
          reason = "用户为儿童，该药物禁用";
        } else if ((g.contains("老年") || g.contains("老人")) && profile.age() != null && profile.age() >= 65) {
          matched = true;
          reason = "用户为老年，该药物慎用/禁用";
        } else if (g.contains("男") && "male".equalsIgnoreCase(profile.gender())) {
          matched = true;
          reason = "该药物对男性有特殊禁忌";
        } else if (g.contains("女") && "female".equalsIgnoreCase(profile.gender())) {
          matched = true;
          reason = "该药物对女性有特殊禁忌";
        }
        if (matched) {
          result.add(new KnowledgeDtos.InteractionRecord(
              "contraindicated_group",
              "high",
              drug, group,
              "药物「" + drug + "」禁用人群：「" + group + "」。" + reason,
              clinical.source()
          ));
        }
      }
    }
    return result;
  }

  // ===== 辅助方法 =====

  private List<MedicationBrief> listUserMedications(long userId) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        """
        SELECT id, name, medicine_type, formula_id
        FROM medications
        WHERE user_id = ? AND enabled = 1
        """,
        userId
    );
    List<MedicationBrief> result = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      result.add(new MedicationBrief(
          longValue(row.get("id")),
          stringValue(row.get("name")),
          stringValue(row.get("medicine_type")),
          row.get("formula_id") instanceof Number n ? n.longValue() : null
      ));
    }
    return result;
  }

  private UserProfileBrief fetchUserProfileBrief(long userId) {
    try {
      Map<String, Object> row = jdbcTemplate.queryForMap(
          """
          SELECT p.age, p.gender, p.height, p.weight, s.focus
          FROM user_profiles p
          LEFT JOIN user_settings s ON s.user_id = p.user_id
          WHERE p.user_id = ?
          """,
          userId
      );
      Integer age = row.get("age") instanceof Number n ? n.intValue() : null;
      String gender = stringValue(row.get("gender"));
      // specialState 暂未在 profile 中建模，默认 null
      return new UserProfileBrief(age, gender, null);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private long longValue(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private record MedicationBrief(long id, String name, String medicineType, Long formulaId) {}

  private record UserProfileBrief(Integer age, String gender, String specialState) {}
}
