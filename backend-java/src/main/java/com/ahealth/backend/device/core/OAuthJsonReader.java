package com.ahealth.backend.device.core;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 从 {@link JsonNode} 中安全读取数值的辅助工具。
 * 所有方法对 null 路径返回 null（不抛异常），适配厂商 API 中字段可选的场景。
 */
public final class OAuthJsonReader {

  private OAuthJsonReader() {}

  public static Integer getInt(JsonNode node, String path) {
    JsonNode v = navigate(node, path);
    if (v == null || v.isNull() || !v.isNumber()) return null;
    return v.asInt();
  }

  public static Long getLong(JsonNode node, String path) {
    JsonNode v = navigate(node, path);
    if (v == null || v.isNull() || !v.isNumber()) return null;
    return v.asLong();
  }

  public static Double getDouble(JsonNode node, String path) {
    JsonNode v = navigate(node, path);
    if (v == null || v.isNull() || !v.isNumber()) return null;
    return v.asDouble();
  }

  public static String getText(JsonNode node, String path) {
    JsonNode v = navigate(node, path);
    if (v == null || v.isNull()) return null;
    if (v.isTextual()) return v.asText();
    if (v.isValueNode()) return v.asText();
    return null;
  }

  public static Boolean getBool(JsonNode node, String path) {
    JsonNode v = navigate(node, path);
    if (v == null || v.isNull() || !v.isBoolean()) return null;
    return v.asBoolean();
  }

  /**
   * 按点分路径导航（如 "data.heart_rate.avg"）。
   * 数组下标支持：data.items[0].value
   */
  public static JsonNode navigate(JsonNode root, String path) {
    if (root == null || path == null || path.isEmpty()) return null;
    String[] parts = path.split("\\.");
    JsonNode cur = root;
    for (String part : parts) {
      if (cur == null || cur.isNull()) return null;
      // 处理数组下标
      int bracketStart = part.indexOf('[');
      if (bracketStart >= 0) {
        String fieldName = part.substring(0, bracketStart);
        if (!fieldName.isEmpty()) {
          cur = cur.path(fieldName);
        }
        // 依次解析 [0][1]...
        int i = bracketStart;
        while (i < part.length() && part.charAt(i) == '[') {
          int end = part.indexOf(']', i);
          if (end < 0) break;
          try {
            int idx = Integer.parseInt(part.substring(i + 1, end));
            cur = cur.path(idx);
          } catch (NumberFormatException ignored) {
            return null;
          }
          i = end + 1;
        }
      } else {
        cur = cur.path(part);
      }
    }
    return cur == null || cur.isMissingNode() ? null : cur;
  }
}
