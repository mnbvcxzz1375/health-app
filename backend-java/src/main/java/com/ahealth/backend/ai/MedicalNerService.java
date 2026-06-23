package com.ahealth.backend.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MedicalNerService {
  private final OpenMedService openMedService;
  private final PiiScrubService piiScrubService;

  public MedicalNerService(OpenMedService openMedService, PiiScrubService piiScrubService) {
    this.openMedService = openMedService;
    this.piiScrubService = piiScrubService;
  }

  /**
   * Extract medication entities from OCR text using PharmaDetect NER.
   * Pipeline: PII scrub → NER extract → group by entity type.
   */
  public List<AiDtos.NerEntity> extractMedicationEntities(String ocrText) {
    if (ocrText == null || ocrText.isBlank()) return List.of();

    // Step 1: Scrub PII before sending to model
    AiDtos.PiiScrubResult scrubResult = piiScrubService.scrub(ocrText);
    String safeText = scrubResult.scrubbedText();

    // Step 2: Call PharmaDetect NER
    List<AiDtos.NerEntity> entities = openMedService.nerExtract(safeText);

    // Step 3: Map masked entities back to original text
    List<AiDtos.NerEntity> restored = new ArrayList<>();
    for (AiDtos.NerEntity entity : entities) {
      String originalText = entity.text();
      for (AiDtos.PiiMask mask : scrubResult.masks()) {
        originalText = originalText.replace(mask.maskToken(), mask.originalText());
      }
      restored.add(new AiDtos.NerEntity(
          originalText, entity.label(), entity.startOffset(), entity.endOffset(), entity.confidence()));
    }
    return restored;
  }

  /**
   * Parse NER entities into structured medication fields.
   * Groups entities by label and maps to medication DTO fields.
   */
  public Map<String, String> entitiesToMedicationFields(List<AiDtos.NerEntity> entities) {
    Map<String, String> fields = new HashMap<>();
    for (AiDtos.NerEntity entity : entities) {
      String label = entity.label().toUpperCase();
      if (label.contains("DRUG") || label.contains("MEDICATION") || label.contains("CHEMICAL")) {
        fields.putIfAbsent("name", entity.text());
      } else if (label.contains("DOSAGE") || label.contains("STRENGTH")) {
        fields.putIfAbsent("dosage", entity.text());
      } else if (label.contains("FREQUENCY") || label.contains("DURATION")) {
        fields.putIfAbsent("usage", entity.text());
      } else if (label.contains("ROUTE") || label.contains("FORM")) {
        fields.putIfAbsent("route", entity.text());
      }
    }
    return fields;
  }
}
