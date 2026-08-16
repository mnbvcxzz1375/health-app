package com.ahealth.backend.consult;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Conservative, deterministic guard for explicit emergency-symptom queries. */
@Service
public class ConsultSafetyService {

  private static final List<Rule> EMERGENCY_RULES = List.of(
      new Rule("CHEST_PAIN", List.of("胸痛", "胸口痛", "胸闷伴出汗", "心前区痛")),
      new Rule("BREATHING_DISTRESS", List.of("呼吸困难", "喘不过气", "无法呼吸", "严重气短")),
      new Rule("STROKE_WARNING", List.of("一侧无力", "口角歪", "说话不清", "突然失语")),
      new Rule("LOSS_OF_CONSCIOUSNESS", List.of("昏迷", "失去意识", "晕倒不醒", "叫不醒")),
      new Rule("SEVERE_BLEEDING", List.of("大出血", "呕血", "便血", "黑便", "咯血")),
      new Rule("SEVERE_ALLERGY", List.of("过敏性休克", "喉头水肿", "全身荨麻疹呼吸困难"))
  );

  public ConsultDtos.SafetyInfo assess(String question) {
    String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
    List<String> flags = EMERGENCY_RULES.stream()
        .filter(rule -> rule.matches(normalized))
        .map(Rule::flag)
        .toList();
    if (!flags.isEmpty()) {
      return new ConsultDtos.SafetyInfo(
          "emergency", flags,
          "仅凭文字无法判断病因或严重程度，不能据此延误紧急处理。",
          "请立即停止训练和自行调整用药，尽快联系急救服务或前往最近的急诊医疗机构。",
          List.of("STOP_ACTIVITY", "EMERGENCY_CARE", "NO_DOSAGE_INFERENCE")
      );
    }
    return new ConsultDtos.SafetyInfo(
        "routine", List.of(),
        "回答仅基于已提供的信息和可检索知识，不能替代面对面评估。",
        "如症状加重、出现新的异常，或对用药存在疑问，请联系医生或药师。",
        List.of("REASSESS_BEFORE_PROGRESSION")
    );
  }

  public boolean requiresUrgentEscalation(ConsultDtos.SafetyInfo safety) {
    return safety != null && "emergency".equals(safety.level());
  }

  private record Rule(String flag, List<String> keywords) {
    boolean matches(String question) {
      return keywords.stream().anyMatch(question::contains);
    }
  }
}
