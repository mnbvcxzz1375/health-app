package com.ahealth.backend.consult;

import java.util.List;

public final class ConsultDtos {
  private ConsultDtos() {}

  public record ConsultQuestionRequest(
      String question,
      String scene
  ) {}

  public record ConsultResponse(
      String requestId,
      String answer,
      List<String> suggestions,
      String disclaimer
  ) {}
}
