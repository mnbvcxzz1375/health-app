package com.ahealth.backend.boneage;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 骨龄评估 API：
 * - POST /api/bone-age/estimate  上传左手腕 X 光图片，返回骨龄评估结果
 * - GET  /api/bone-age/recent    查询用户最近评估记录
 *
 * 也作为 UploadController.createTaskByCustomModel(type="bone") 的底层实现。
 */
@RestController
@RequestMapping("/api/bone-age")
public class BoneAgeController {
  private final BoneAgeService boneAgeService;

  public BoneAgeController(BoneAgeService boneAgeService) {
    this.boneAgeService = boneAgeService;
  }

  /** 上传 X 光图片进行骨龄评估 */
  @PostMapping("/estimate")
  public BoneAgeService.BoneAgeEstimateResponse estimate(
      @RequestParam("file") MultipartFile file
  ) {
    return boneAgeService.estimate(file);
  }

  /** 查询当前用户最近 N 条骨龄评估记录（默认 10 条） */
  @GetMapping("/recent")
  public List<BoneAgeService.BoneAgeTaskRecord> recent(
      @RequestParam(defaultValue = "10") int limit
  ) {
    if (limit < 1) limit = 1;
    if (limit > 50) limit = 50;
    return boneAgeService.listRecent(limit);
  }
}
