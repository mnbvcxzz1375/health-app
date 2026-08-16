package com.ahealth.backend.consult;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/consult")
public class ConsultController {
  private final ConsultService consultService;
  private final ObjectMapper objectMapper;
  private final ExecutorService executorService = Executors.newCachedThreadPool();

  public ConsultController(ConsultService consultService, ObjectMapper objectMapper) {
    this.consultService = consultService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/questions")
  public ConsultDtos.ConsultResponse ask(@RequestBody ConsultDtos.ConsultQuestionRequest request) {
    return consultService.ask(request);
  }

  @PostMapping("/stream")
  public void stream(@RequestBody ConsultDtos.ConsultQuestionRequest request, HttpServletResponse response)
      throws IOException {
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("application/x-ndjson; charset=utf-8");
    response.setHeader("Cache-Control", "no-cache, no-transform");
    response.setHeader("Connection", "keep-alive");
    response.setHeader("X-Accel-Buffering", "no");

    ConsultDtos.ConsultResponse result = consultService.ask(request);
    response.getWriter().write(objectMapper.writeValueAsString(Map.of("type", "chunk", "delta", "正在分析你的问题，请稍候。")) + "\n");
    response.flushBuffer();

    int chunkSize = 18;
    for (int start = 0; start < result.answer().length(); start += chunkSize) {
      int end = Math.min(result.answer().length(), start + chunkSize);
      response.getWriter().write(objectMapper.writeValueAsString(Map.of("type", "chunk", "delta", result.answer().substring(start, end))) + "\n");
      response.flushBuffer();
    }

    response.getWriter().write(objectMapper.writeValueAsString(Map.of(
        "type", "complete",
        "requestId", result.requestId(),
        "answer", result.answer(),
        "suggestions", result.suggestions(),
        "disclaimer", result.disclaimer(),
        "evidence", result.evidence(),
        "safety", result.safety()
    )) + "\n");
    response.flushBuffer();
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamConsult(
      @RequestParam String question,
      @RequestParam(defaultValue = "assistant") String scene) {
    SseEmitter emitter = new SseEmitter(60_000L);
    executorService.submit(() -> {
      try {
        ConsultDtos.ConsultResponse response = consultService.ask(
            new ConsultDtos.ConsultQuestionRequest(question, scene));
        String answer = response.answer();
        String[] chunks = answer.split("(?<=[。！？\\n])");
        for (String chunk : chunks) {
          if (!chunk.isBlank()) {
            emitter.send(SseEmitter.event().data(chunk.trim()));
            Thread.sleep(50);
          }
        }
        emitter.send(SseEmitter.event().name("done").data(
            objectMapper.writeValueAsString(Map.of(
                "requestId", response.requestId(),
                "suggestions", response.suggestions(),
                "disclaimer", response.disclaimer(),
                "evidence", response.evidence(),
                "safety", response.safety()))));
        emitter.complete();
      } catch (Exception e) {
        emitter.completeWithError(e);
      }
    });
    return emitter;
  }

  @GetMapping("/history")
  public List<Map<String, Object>> getHistory(
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    return consultService.getHistory(limit, offset);
  }

  @DeleteMapping("/history/{id}")
  public Map<String, Object> deleteHistory(@PathVariable int id) {
    return consultService.deleteHistoryItem(id);
  }

  @DeleteMapping("/history")
  public Map<String, Object> clearHistory() {
    return consultService.clearHistory();
  }
}
