package com.ahealth.backend.ai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DdiKnowledgeService {
  private final JdbcTemplate jdbc;

  public DdiKnowledgeService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Check a list of drug names against the DDI knowledge base.
   * Returns warnings for any known interactions.
   */
  public List<AiDtos.DdiWarning> checkInteractions(List<String> drugNames) {
    if (drugNames == null || drugNames.size() < 2) return List.of();

    List<AiDtos.DdiWarning> warnings = new ArrayList<>();

    // Check all pairs
    for (int i = 0; i < drugNames.size(); i++) {
      for (int j = i + 1; j < drugNames.size(); j++) {
        String a = drugNames.get(i);
        String b = drugNames.get(j);
        List<AiDtos.DdiWarning> found = findInteraction(a, b);
        warnings.addAll(found);
      }
    }

    return warnings;
  }

  private List<AiDtos.DdiWarning> findInteraction(String drugA, String drugB) {
    // Match in both directions (A->B and B->A)
    var rows = jdbc.queryForList(
        "SELECT drug_a, drug_b, severity, description, recommendation "
        + "FROM ddi_knowledge WHERE (drug_a LIKE ? AND drug_b LIKE ?) OR (drug_a LIKE ? AND drug_b LIKE ?)",
        "%" + drugA + "%", "%" + drugB + "%",
        "%" + drugB + "%", "%" + drugA + "%"
    );

    List<AiDtos.DdiWarning> warnings = new ArrayList<>();
    for (var row : rows) {
      warnings.add(new AiDtos.DdiWarning(
          (String) row.get("drug_a"),
          (String) row.get("drug_b"),
          (String) row.get("severity"),
          (String) row.get("description"),
          (String) row.get("recommendation")
      ));
    }
    return warnings;
  }
}
