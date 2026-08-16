package com.ahealth.backend.ai;

import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.security.AdminAccessService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 模板管理端点：列出、新建版本、激活/回滚、查看历史版本。
 *
 * <p>路径前缀 /api/admin/prompts，所有端点都需要管理员认证。
 */
@RestController
@RequestMapping("/api/admin/prompts")
public class PromptTemplateController {

  private final PromptTemplateService promptTemplateService;
  private final AdminAccessService adminAccessService;

  public PromptTemplateController(PromptTemplateService promptTemplateService,
      AdminAccessService adminAccessService) {
    this.promptTemplateService = promptTemplateService;
    this.adminAccessService = adminAccessService;
  }

  /** 列出激活模板（可按 key/scene 过滤）。 */
  @GetMapping
  public PromptTemplateDtos.ListResponse list(
      @RequestParam(required = false) String key,
      @RequestParam(required = false) String scene) {
    adminAccessService.requireAdmin();
    List<PromptTemplateService.PromptTemplate> items = promptTemplateService.listActive(key, scene);
    List<PromptTemplateDtos.PromptTemplateView> views = items.stream()
        .map(PromptTemplateDtos.PromptTemplateView::from)
        .toList();
    return new PromptTemplateDtos.ListResponse(views, views.size());
  }

  /** 创建新版本。 */
  @PostMapping
  public PromptTemplateDtos.SingleResponse upsert(@RequestBody PromptTemplateDtos.UpsertPromptRequest request) {
    adminAccessService.requireAdmin();
    if (request.templateKey() == null || request.templateKey().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "templateKey 不能为空");
    }
    if (request.scene() == null || request.scene().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "scene 不能为空");
    }
    if (request.content() == null || request.content().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "content 不能为空");
    }
    PromptTemplateService.PromptTemplate t = promptTemplateService.upsert(
        request.templateKey(),
        request.scene(),
        request.content(),
        request.variables(),
        request.description());
    if (t == null) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "模板保存失败");
    }
    return new PromptTemplateDtos.SingleResponse(PromptTemplateDtos.PromptTemplateView.from(t));
  }

  /** 激活指定 id 的版本。 */
  @PostMapping("/{id}/activate")
  public PromptTemplateDtos.SingleResponse activate(@PathVariable int id) {
    adminAccessService.requireAdmin();
    PromptTemplateService.PromptTemplate t = promptTemplateService.activate(id);
    if (t == null) {
      throw new ApiException(HttpStatus.NOT_FOUND, "模板版本不存在");
    }
    return new PromptTemplateDtos.SingleResponse(PromptTemplateDtos.PromptTemplateView.from(t));
  }

  /** 回滚到指定 id 的版本（语义等价于 activate）。 */
  @PostMapping("/{id}/rollback")
  public PromptTemplateDtos.SingleResponse rollback(@PathVariable int id) {
    adminAccessService.requireAdmin();
    PromptTemplateService.PromptTemplate t = promptTemplateService.rollback(id);
    if (t == null) {
      throw new ApiException(HttpStatus.NOT_FOUND, "模板版本不存在");
    }
    return new PromptTemplateDtos.SingleResponse(PromptTemplateDtos.PromptTemplateView.from(t));
  }

  /** 查看指定 key 的所有历史版本。 */
  @GetMapping("/{key}/versions")
  public PromptTemplateDtos.ListResponse versions(@PathVariable String key) {
    adminAccessService.requireAdmin();
    List<PromptTemplateService.PromptTemplate> items = promptTemplateService.listVersions(key);
    List<PromptTemplateDtos.PromptTemplateView> views = items.stream()
        .map(PromptTemplateDtos.PromptTemplateView::from)
        .toList();
    return new PromptTemplateDtos.ListResponse(views, views.size());
  }
}
