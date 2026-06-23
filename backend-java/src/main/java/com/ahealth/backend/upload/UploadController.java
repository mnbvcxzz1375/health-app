package com.ahealth.backend.upload;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/analyze")
public class UploadController {
  private final UploadService uploadService;

  public UploadController(UploadService uploadService) {
    this.uploadService = uploadService;
  }

  @PostMapping("/tasks")
  public UploadDtos.AnalyzeTaskResponse createTask(
      @RequestParam(defaultValue = "text") String type,
      @RequestParam(defaultValue = "") String text,
      @RequestParam(value = "files", required = false) MultipartFile[] files
  ) {
    return uploadService.createTask(type, text, files);
  }

  @PostMapping("/tasks/custom-model")
  public UploadDtos.AnalyzeTaskResponse createTaskByCustomModel(
      @RequestParam(defaultValue = "text") String type,
      @RequestParam(defaultValue = "") String text,
      @RequestParam(value = "files", required = false) MultipartFile[] files
  ) {
    return uploadService.createTaskByCustomModel(type, text, files);
  }

  @GetMapping("/tasks/{taskId}")
  public UploadDtos.AnalyzeResultResponse getTask(@PathVariable String taskId) {
    return uploadService.getTask(taskId);
  }

  @PostMapping("/tasks/{taskId}/save")
  public UploadDtos.AnalyzeSaveResponse saveTask(@PathVariable String taskId) {
    return uploadService.saveTask(taskId);
  }

  @DeleteMapping("/tasks/{taskId}")
  public Map<String, Boolean> deleteTask(@PathVariable String taskId) {
    return uploadService.deleteTask(taskId);
  }

  @GetMapping("/reports")
  public List<UploadDtos.SavedAnalyzeReport> reports() {
    return uploadService.listSavedReports();
  }
}
