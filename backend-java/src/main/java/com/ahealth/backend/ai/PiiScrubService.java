package com.ahealth.backend.ai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PiiScrubService {
  private final OpenMedService openMedService;

  public PiiScrubService(OpenMedService openMedService) {
    this.openMedService = openMedService;
  }

  /**
   * Detect PII in text and replace with mask tokens.
   * Returns scrubbed text + mapping table for restoration.
   * If OpenMed is not configured, returns original text unchanged.
   */
  public AiDtos.PiiScrubResult scrub(String text) {
    if (text == null || text.isBlank()) {
      return new AiDtos.PiiScrubResult(text, List.of());
    }
    if (!openMedService.isConfigured()) {
      return new AiDtos.PiiScrubResult(text, List.of());
    }

    try {
      List<AiDtos.PiiMask> masks = openMedService.piiDetect(text);
      if (masks.isEmpty()) {
        return new AiDtos.PiiScrubResult(text, List.of());
      }

      // Apply masks from end to start to preserve offsets
      String scrubbed = text;
      List<AiDtos.PiiMask> sorted = new ArrayList<>(masks);
      sorted.sort((a, b) -> Integer.compare(b.startOffset(), a.startOffset()));

      for (AiDtos.PiiMask mask : sorted) {
        if (mask.startOffset() >= 0 && mask.endOffset() <= scrubbed.length()
            && mask.startOffset() < mask.endOffset()) {
          scrubbed = scrubbed.substring(0, mask.startOffset())
              + mask.maskToken()
              + scrubbed.substring(mask.endOffset());
        }
      }
      return new AiDtos.PiiScrubResult(scrubbed, masks);
    } catch (Exception e) {
      // PII scrubbing is best-effort; don't block the pipeline
      return new AiDtos.PiiScrubResult(text, List.of());
    }
  }

  /**
   * Restore original PII text from scrubbed text using the mapping table.
   * Used when returning results to the user.
   */
  public String restore(String scrubbedText, List<AiDtos.PiiMask> masks) {
    if (scrubbedText == null || masks == null || masks.isEmpty()) return scrubbedText;
    String restored = scrubbedText;
    for (AiDtos.PiiMask mask : masks) {
      restored = restored.replace(mask.maskToken(), mask.originalText());
    }
    return restored;
  }
}
