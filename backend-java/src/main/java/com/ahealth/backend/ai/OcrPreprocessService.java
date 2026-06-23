package com.ahealth.backend.ai;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class OcrPreprocessService {

  // === Character confusion map for medication OCR ===
  // Based on agricultural OCR project's approach, adapted for pharmaceutical domain
  private static final Map<String, String> DRUG_CONFUSION_MAP = Map.ofEntries(
      // Chemical name variants
      Map.entry("胺氨铵", "胺"),
      Map.entry("膦麟鏻", "膦"),
      Map.entry("氯绿", "氯"),
      Map.entry("磺黄", "磺"),
      Map.entry("霉素", "霉素"),  // keep
      Map.entry("素", "素"),      // keep
      // Dosage form confusions
      Map.entry("胶嚷", "胶囊"),
      Map.entry("片荆", "片剂"),
      Map.entry("口服腋", "口服液"),
      Map.entry("颗粒", "颗粒"),  // keep
      // Common OCR misreads
      Map.entry("己已巳", "已"),
      Map.entry("第笫弟的", "第"),
      Map.entry("字学宇宁", "字"),
      Map.entry("证诞正", "证"),
      Map.entry("号", "号"),
      // Number confusions
      Map.entry("０", "0"), Map.entry("１", "1"), Map.entry("２", "2"),
      Map.entry("３", "3"), Map.entry("４", "4"), Map.entry("５", "5"),
      Map.entry("６", "6"), Map.entry("７", "7"), Map.entry("８", "8"),
      Map.entry("９", "9")
  );

  // === Dosage unit patterns ===
  private static final Pattern DOSAGE_PATTERN = Pattern.compile(
      "(\\d+(?:\\.\\d+)?)\\s*(mg|g|ml|μg|mcg|片|粒|袋|支|瓶|滴|丸)", Pattern.CASE_INSENSITIVE);

  // === Frequency patterns ===
  private static final Pattern FREQ_CN_PATTERN = Pattern.compile(
      "[一每]?天\\s*(\\d|[一二三四五六])\\s*次");
  private static final Pattern FREQ_EN_PATTERN = Pattern.compile(
      "\\b(qd|bid|tid|qid|q\\d+h|prn|qn|qod)\\b", Pattern.CASE_INSENSITIVE);

  // === Drug name blacklist (skip these as drug names) ===
  private static final Set<String> DRUG_NAME_BLACKLIST = Set.of(
      "用法", "用量", "注意事项", "禁忌", "不良反应", "适应症", "规格",
      "批准文号", "生产企业", "有效期", "生产日期", "批号", "贮藏",
      "成份", "性状", "包装", "执行标准"
  );

  /**
   * Full preprocessing pipeline for OCR text from medication images.
   * Pipeline: normalize → correct confusions → extract structured fields.
   */
  public PreprocessedOcrResult preprocess(String rawText) {
    if (rawText == null || rawText.isBlank()) {
      return new PreprocessedOcrResult("", Map.of(), List.of());
    }

    // Step 1: Text normalization
    String normalized = normalizeText(rawText);

    // Step 2: Character confusion correction
    String corrected = correctConfusions(normalized);

    // Step 3: Extract structured fields
    Map<String, String> fields = extractDrugFields(corrected);

    // Step 4: Extract raw lines for NER input
    List<String> lines = Arrays.stream(corrected.split("\\n"))
        .map(String::trim)
        .filter(l -> !l.isBlank())
        .toList();

    return new PreprocessedOcrResult(corrected, fields, lines);
  }

  /**
   * Text normalization pipeline (from agricultural OCR project).
   * Handles full-width chars, zero-width chars, whitespace, Unicode normalization.
   */
  public String normalizeText(String text) {
    if (text == null) return "";

    String result = text;

    // Full-width to half-width conversion (ASCII range)
    char[] chars = result.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      if (chars[i] >= '！' && chars[i] <= '～') {
        chars[i] = (char) (chars[i] - 0xFEE0);
      } else if (chars[i] == '　') {
        chars[i] = ' '; // ideographic space → space
      }
    }
    result = new String(chars);

    // Percent sign normalization
    result = result.replace("％", "%");

    // Zero-width character removal (U+200B-U+200F, U+202A-U+202E, U+FEFF)
    result = result.replaceAll("[\\u200B-\\u200F\\u202A-\\u202E\\uFEFF]", "");

    // Whitespace normalization
    result = result.replaceAll("[ \\t]+", " ");
    result = result.replaceAll("\\n{3,}", "\n\n");

    return result.trim();
  }

  /**
   * Character confusion correction for medication text.
   * Maps commonly misrecognized characters to their correct forms.
   */
  public String correctConfusions(String text) {
    if (text == null) return "";
    String result = text;

    // Apply character-by-character confusion map
    char[] chars = result.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      for (Map.Entry<String, String> entry : DRUG_CONFUSION_MAP.entrySet()) {
        if (entry.getKey().indexOf(chars[i]) >= 0) {
          chars[i] = entry.getValue().charAt(0);
          break;
        }
      }
    }
    return new String(chars);
  }

  /**
   * Extract structured drug fields from preprocessed OCR text.
   * Returns map with keys: name, dosage, frequency, usage, warnings, manufacturer, expiryDate
   */
  public Map<String, String> extractDrugFields(String text) {
    Map<String, String> fields = new LinkedHashMap<>();

    // Extract dosage
    Matcher dosageMatcher = DOSAGE_PATTERN.matcher(text);
    if (dosageMatcher.find()) {
      fields.put("dosageValue", dosageMatcher.group(1));
      fields.put("dosageUnit", dosageMatcher.group(2));
    }

    // Extract frequency (Chinese)
    Matcher freqCn = FREQ_CN_PATTERN.matcher(text);
    if (freqCn.find()) {
      fields.put("frequency", freqCn.group());
    } else {
      // Extract frequency (English)
      Matcher freqEn = FREQ_EN_PATTERN.matcher(text);
      if (freqEn.find()) {
        fields.put("frequency", freqEn.group().toUpperCase());
      }
    }

    // Extract usage timing
    if (text.contains("饭前") || text.contains("餐前")) fields.put("usage", "饭前");
    else if (text.contains("饭后") || text.contains("餐后")) fields.put("usage", "饭后");
    else if (text.contains("随餐")) fields.put("usage", "随餐");
    else if (text.contains("睡前")) fields.put("usage", "睡前");
    else if (text.contains("空腹")) fields.put("usage", "空腹");

    // Extract drug name (first line that looks like a drug name)
    String[] lines = text.split("\\n");
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.length() >= 2 && trimmed.length() <= 30
          && !DRUG_NAME_BLACKLIST.contains(trimmed)
          && !trimmed.matches(".*\\d{4,}.*")  // skip numbers like batch/license
          && !trimmed.contains("：")           // skip labeled lines (full-width colon)
          && !trimmed.contains(":")) {
        fields.putIfAbsent("name", trimmed);
        break;
      }
    }

    // Extract warnings/contraindications
    List<String> warnings = new ArrayList<>();
    for (String line : lines) {
      String lower = line.toLowerCase();
      if (lower.contains("禁忌") || lower.contains("禁用") || lower.contains("慎用")
          || lower.contains("过敏") || lower.contains("不宜")) {
        warnings.add(line.trim());
      }
    }
    if (!warnings.isEmpty()) {
      fields.put("warnings", String.join("；", warnings));
    }

    // Extract manufacturer
    for (String line : lines) {
      if (line.contains("药业") || line.contains("制药") || line.contains("药厂")
          || line.contains("有限公司") || line.contains("股份")) {
        fields.putIfAbsent("manufacturer", line.trim());
      }
    }

    // Extract expiry date
    Pattern expiryPattern = Pattern.compile(
        "(?:有效期|效期|失效日期)[至到：:]?\\s*(\\d{4}[年./-]\\d{1,2}[月./-]?\\d{0,2}[日]?)");
    Matcher expiryMatcher = expiryPattern.matcher(text);
    if (expiryMatcher.find()) {
      fields.put("expiryDate", expiryMatcher.group(1));
    }

    return fields;
  }

  /** Result of OCR preprocessing */
  public record PreprocessedOcrResult(
      String cleanedText,
      Map<String, String> extractedFields,
      List<String> lines
  ) {}
}
