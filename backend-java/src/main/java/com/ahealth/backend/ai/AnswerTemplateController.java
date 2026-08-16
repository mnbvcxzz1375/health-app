package com.ahealth.backend.ai;

import com.ahealth.backend.security.AdminAccessService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 答案模板 admin 管理端点。
 *
 * <p>路径前缀：{@code /api/admin/answers}
 *
 * <p>端点：
 * <ul>
 *   <li>{@code GET /api/admin/answers?scene=&key=} — 列出模板（可选过滤）</li>
 *   <li>{@code GET /api/admin/answers/active?scene=} — 列出激活模板</li>
 *   <li>{@code GET /api/admin/answers/{key}/versions} — 查看历史版本</li>
 *   <li>{@code POST /api/admin/answers} — upsert（新建/更新版本）</li>
 *   <li>{@code POST /api/admin/answers/{id}/activate} — 激活指定版本</li>
 *   <li>{@code DELETE /api/admin/answers/{id}} — 删除指定版本</li>
 *   <li>{@code POST /api/admin/answers/test?question=&scene=} — 测试匹配（不更新 hit_count）</li>
 *   <li>{@code GET /api/admin/answers/stats} — 统计信息</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/answers")
public class AnswerTemplateController {

  private final AnswerTemplateService answerTemplateService;
  private final AdminAccessService adminAccessService;

  public AnswerTemplateController(AnswerTemplateService answerTemplateService,
      AdminAccessService adminAccessService) {
    this.answerTemplateService = answerTemplateService;
    this.adminAccessService = adminAccessService;
  }

  /** 列出模板（支持按 scene / key 过滤，返回所有版本）。 */
  @GetMapping
  public List<AnswerTemplateService.AnswerTemplate> list(
      @RequestParam(required = false) String scene,
      @RequestParam(required = false) String key) {
    adminAccessService.requireAdmin();
    return answerTemplateService.list(scene, key);
  }

  /** 列出激活模板（仅 active=1，按 priority DESC 排序）。 */
  @GetMapping("/active")
  public List<AnswerTemplateService.AnswerTemplate> listActive(
      @RequestParam(defaultValue = "consult") String scene) {
    adminAccessService.requireAdmin();
    return answerTemplateService.listActive(scene);
  }

  /** 查看某 template_key 的所有版本。 */
  @GetMapping("/{key}/versions")
  public List<AnswerTemplateService.AnswerTemplate> versions(@PathVariable String key) {
    adminAccessService.requireAdmin();
    return answerTemplateService.listVersions(key);
  }

  /** upsert：新建更高版本并自动激活。 */
  @PostMapping
  public AnswerTemplateDtos.UpsertResponse upsert(@RequestBody AnswerTemplateDtos.UpsertRequest req) {
    adminAccessService.requireAdmin();
    AnswerTemplateService.UpsertRequest serviceReq = new AnswerTemplateService.UpsertRequest(
        req.templateKey(), req.scene(), req.category(), req.keywords(),
        req.pattern(), req.templateText(), req.variables(), req.priority());
    int newVersion = answerTemplateService.upsert(serviceReq);
    return new AnswerTemplateDtos.UpsertResponse(
        newVersion, req.templateKey(), "已创建 v" + newVersion + " 并自动激活");
  }

  /** 激活指定版本（按 id）。 */
  @PostMapping("/{id}/activate")
  public Map<String, Object> activate(@PathVariable int id) {
    adminAccessService.requireAdmin();
    answerTemplateService.activate(id);
    return Map.of("id", id, "activated", true);
  }

  /** 删除指定版本。 */
  @DeleteMapping("/{id}")
  public Map<String, Object> delete(@PathVariable int id) {
    adminAccessService.requireAdmin();
    answerTemplateService.delete(id);
    return Map.of("id", id, "deleted", true);
  }

  /** 测试匹配：不更新 hit_count，用于验证模板规则。 */
  @PostMapping("/test")
  public AnswerTemplateDtos.TestResponse test(
      @RequestParam String question,
      @RequestParam(defaultValue = "consult") String scene) {
    adminAccessService.requireAdmin();
    var match = answerTemplateService.testMatch(question, scene);
    if (match.isEmpty()) {
      return new AnswerTemplateDtos.TestResponse(false, null, scene, null, null);
    }
    var result = match.get();
    return new AnswerTemplateDtos.TestResponse(
        true,
        result.template().templateKey(),
        result.template().scene(),
        result.confidence(),
        result.renderedText());
  }

  /** 统计信息：各 scene 模板数、总命中数、缓存大小。 */
  @GetMapping("/stats")
  public Map<String, Object> stats() {
    adminAccessService.requireAdmin();
    return answerTemplateService.stats();
  }
}
