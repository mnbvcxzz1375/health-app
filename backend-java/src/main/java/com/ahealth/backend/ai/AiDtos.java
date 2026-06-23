package com.ahealth.backend.ai;

import java.util.List;
import java.util.Map;

public final class AiDtos {
  private AiDtos() {}

  /** NER entity extracted by PharmaDetect */
  public record NerEntity(
      String text,       // e.g. "阿莫西林"
      String label,      // e.g. "DRUG_NAME", "DOSAGE", "FREQUENCY"
      int startOffset,
      int endOffset,
      double confidence
  ) {}

  /** PII detection result */
  public record PiiMask(
      String originalText,  // e.g. "张明"
      String maskToken,     // e.g. "[PERSON_1]"
      String piiType,       // e.g. "PERSON", "PHONE", "ID_CARD"
      int startOffset,
      int endOffset
  ) {}

  /** PII scrubbing result with mapping table */
  public record PiiScrubResult(
      String scrubbedText,
      List<PiiMask> masks
  ) {}

  /** Model routing decision */
  public record ModelRouteDecision(
      String selectedModel,   // e.g. "pharmadetect", "dashscope", "medvl"
      String reason,          // e.g. "medication text → PharmaDetect NER"
      boolean piiScrubApplied
  ) {}

  /** Drug-drug interaction warning */
  public record DdiWarning(
      String drugA,
      String drugB,
      String severity,      // "high", "moderate", "low"
      String description,
      String recommendation
  ) {}

  /** Enhanced medication recognition with NER */
  public record NerRecognitionResult(
      String name,
      String alias,
      Integer dosageValue,
      String dosageUnit,
      String usage,
      String notes,
      Double confidence,
      List<NerEntity> entities,
      String sourceText
  ) {}
}
