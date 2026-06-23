package com.ahealth.backend.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonSupport {
  private final ObjectMapper objectMapper;

  public JsonSupport(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("JSON 序列化失败", exception);
    }
  }

  public List<String> readStringList(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(raw, new TypeReference<>() {});
    } catch (JsonProcessingException exception) {
      return List.of();
    }
  }

  public Map<String, Object> readObject(String raw) {
    if (raw == null || raw.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(raw, new TypeReference<>() {});
    } catch (JsonProcessingException exception) {
      return Map.of();
    }
  }
}
