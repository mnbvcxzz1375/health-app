package com.ahealth.backend.rag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 文本切片服务：将长文本切成适合向量检索的 chunk。
 *
 * <p>策略：
 * <ul>
 *   <li>短文本（&lt; 256 字符）：不切片，原样返回</li>
 *   <li>中等文本（256-1024 字符）：按段落切片，每段不超过 512 字符</li>
 *   <li>长文本（&gt; 1024 字符）：递归字符切片，chunk_size=512，overlap=64</li>
 * </ul>
 */
@Service
public class ChunkingService {

  private static final int SHORT_THRESHOLD = 256;
  private static final int CHUNK_SIZE = 512;
  private static final int OVERLAP = 64;

  /** 将文本切片，返回非空 chunk 列表。 */
  public List<String> chunk(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    String normalized = text.trim();
    if (normalized.length() <= SHORT_THRESHOLD) {
      return List.of(normalized);
    }
    if (normalized.length() <= 1024) {
      return chunkByParagraph(normalized);
    }
    return chunkByChar(normalized);
  }

  /** 按段落（双换行）切片，超长段落再按字符切。 */
  private List<String> chunkByParagraph(String text) {
    List<String> result = new ArrayList<>();
    String[] paragraphs = text.split("\\n\\s*\\n");
    StringBuilder current = new StringBuilder();
    for (String para : paragraphs) {
      String trimmed = para.trim();
      if (trimmed.isEmpty()) continue;
      if (current.length() + trimmed.length() + 2 > CHUNK_SIZE && current.length() > 0) {
        result.add(current.toString().trim());
        current = new StringBuilder();
      }
      if (trimmed.length() > CHUNK_SIZE) {
        // 单段超长，递归切
        result.addAll(chunkByChar(trimmed));
      } else {
        if (current.length() > 0) current.append("\n\n");
        current.append(trimmed);
      }
    }
    if (current.length() > 0) {
      result.add(current.toString().trim());
    }
    return result;
  }

  /** 递归字符切片，chunk_size=512，overlap=64。 */
  private List<String> chunkByChar(String text) {
    List<String> result = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      int end = Math.min(start + CHUNK_SIZE, text.length());
      // 尽量在句号/换行处截断
      int breakPoint = findBreakPoint(text, start, end);
      if (breakPoint > start) end = breakPoint;
      String chunk = text.substring(start, end).trim();
      if (!chunk.isEmpty()) {
        result.add(chunk);
      }
      if (end >= text.length()) break;
      start = end - OVERLAP;
      if (start < 0) start = 0;
    }
    return result;
  }

  /** 在 [start, end] 范围内找到最靠后的句号/换行符位置。 */
  private int findBreakPoint(String text, int start, int end) {
    // 优先在 end 之前找 . ! ? 。 ！ ？ \n
    for (int i = end - 1; i > start + 100; i--) {
      char c = text.charAt(i);
      if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
        return i + 1;
      }
    }
    // 退而在 end 之前找空格
    for (int i = end - 1; i > start + 100; i--) {
      if (text.charAt(i) == ' ' || text.charAt(i) == ',') {
        return i + 1;
      }
    }
    return end;
  }

  /** 估算 token 数（粗略：中文按 1 字 ≈ 1 token，英文按 4 字符 ≈ 1 token）。 */
  public int estimateTokens(String text) {
    if (text == null || text.isEmpty()) return 0;
    int chinese = 0;
    int other = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c >= 0x4E00 && c <= 0x9FFF) {
        chinese++;
      } else {
        other++;
      }
    }
    return chinese + other / 4;
  }
}
