package com.ahealth.backend.consult;

import java.util.List;

public final class ConsultDtos {
  private ConsultDtos() {}

  public record ConsultQuestionRequest(
      String question,
      String scene
  ) {}

  public record ConsultEvidence(
      String id,
      String title,
      String sourceType,
      String field,
      String excerpt,
      Double retrievalScore
  ) {}

  public record SafetyInfo(
      String level,
      java.util.List<String> flags,
      String uncertainty,
      String escalation,
      java.util.List<String> actionTags
  ) {
    public SafetyInfo {
      flags = flags == null ? List.of() : List.copyOf(flags);
      actionTags = actionTags == null ? List.of() : List.copyOf(actionTags);
    }

    public SafetyInfo(String level, java.util.List<String> flags, String uncertainty,
        String escalation) {
      this(level, flags, uncertainty, escalation, java.util.List.of());
    }
  }

  public record ConsultResponse(
      String requestId,
      String answer,
      List<String> suggestions,
      String disclaimer,
      List<ConsultEvidence> evidence,
      SafetyInfo safety
  ) {
    public ConsultResponse(String requestId, String answer, List<String> suggestions, String disclaimer) {
      this(
          requestId,
          answer,
          suggestions,
          disclaimer,
          List.of(),
          new SafetyInfo(
              "routine",
              List.of("SAFETY_METADATA_UNAVAILABLE"),
              "该历史响应未包含可追溯安全元数据。",
              "如有不适或用药疑问，请联系医生或药师。",
              List.of("NO_PERSONALIZED_GUIDANCE", "REQUEST_MORE_EVIDENCE")
          )
      );
    }
  }
}
