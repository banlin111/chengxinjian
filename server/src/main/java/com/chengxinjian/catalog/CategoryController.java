package com.chengxinjian.catalog;

import com.chengxinjian.auth.AdminAuthController.OK;
import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.common.ApiResponse;
import com.chengxinjian.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static com.chengxinjian.catalog.CategoryModels.*;

@RestController
@Validated
@RequestMapping("/api/v1")
public class CategoryController {
    private final CategoryService service;
    public CategoryController(CategoryService service) { this.service = service; }
    @GetMapping("/categories")
    ApiResponse<List<Category>> published(@RequestParam @Pattern(regexp=KIND) String kind) {
        return ApiResponse.ok(service.published(kind));
    }
    @GetMapping("/admin/content/categories")
    @PreAuthorize("hasAuthority('content:read')")
    ApiResponse<Page<Category>> list(@AuthenticationPrincipal AdminPrincipal admin,
        @RequestParam(defaultValue="1") @Min(1) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize,
        @RequestParam(required=false) @Size(max=100) String q,
        @RequestParam(required=false) @Pattern(regexp=STATUS) String status,
        @RequestParam(required=false) String categoryId, @RequestParam(required=false) String courseId,
        @RequestParam(required=false) String teacherId) {
        // 契约的共用查询字段在分类资源没有对应含义，避免静默返回错误筛选结果。
        if (categoryId != null || courseId != null || teacherId != null) {
            throw BusinessException.invalid("分类列表不支持关联内容筛选");
        }
        return ApiResponse.ok(service.list(admin, page, pageSize, q, status));
    }
    @GetMapping("/admin/content/categories/{id}")
    @PreAuthorize("hasAuthority('content:read')")
    ApiResponse<Category> get(@AuthenticationPrincipal AdminPrincipal admin, @PathVariable @Size(max=36) String id) {
        return ApiResponse.ok(service.get(admin, id));
    }
    @PostMapping("/admin/content/categories")
    @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<Category> create(@AuthenticationPrincipal AdminPrincipal admin,
        @RequestHeader("Idempotency-Key") @Size(max=36) String key, @Valid @RequestBody Create input) {
        return ApiResponse.ok(service.create(admin, key, input));
    }
    @PutMapping("/admin/content/categories/{id}")
    @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<Category> update(@AuthenticationPrincipal AdminPrincipal admin, @PathVariable @Size(max=36) String id,
        @Valid @RequestBody Update input) { return ApiResponse.ok(service.update(admin, id, input)); }
    @DeleteMapping("/admin/content/categories/{id}")
    @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<OK> archive(@AuthenticationPrincipal AdminPrincipal admin, @PathVariable @Size(max=36) String id,
        @Valid @RequestBody Archive input) {
        service.archive(admin, id, input); return ApiResponse.ok(new OK(true));
    }
}
