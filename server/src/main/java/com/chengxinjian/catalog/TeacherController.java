package com.chengxinjian.catalog;

import com.chengxinjian.auth.AdminAuthController.OK;
import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.common.ApiResponse;
import com.chengxinjian.common.BusinessException;
import com.chengxinjian.user.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static com.chengxinjian.catalog.TeacherModels.*;
import static com.chengxinjian.catalog.CategoryModels.Page;

@RestController
@Validated
@RequestMapping("/api/v1")
public class TeacherController {
    private final TeacherService service;
    public TeacherController(TeacherService service) { this.service=service; }
    @GetMapping("/teachers")
    ApiResponse<Page<Teacher>> published(@AuthenticationPrincipal UserPrincipal user,
        @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize,
        @RequestParam(required=false) @Size(max=100) String q) { return ApiResponse.ok(service.published(user,page,pageSize,q)); }
    @GetMapping("/teachers/{id}")
    ApiResponse<Teacher> publishedGet(@AuthenticationPrincipal UserPrincipal user,@PathVariable @Size(max=36) String id) {
        return ApiResponse.ok(service.publishedGet(user,id));
    }
    @PutMapping("/me/follows/{teacherId}")
    ApiResponse<OK> follow(@AuthenticationPrincipal UserPrincipal user,@PathVariable @Size(max=36) String teacherId,@Valid @RequestBody Follow input) {
        service.follow(user,teacherId,input.followed());return ApiResponse.ok(new OK(true));
    }
    @GetMapping("/admin/content/teachers")
    @PreAuthorize("hasAuthority('content:read')")
    ApiResponse<Page<AdminTeacher>> list(@AuthenticationPrincipal AdminPrincipal admin,
        @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize,
        @RequestParam(required=false) @Size(max=100) String q,@RequestParam(required=false) @Pattern(regexp=CategoryModels.STATUS) String status,
        @RequestParam(required=false) @Size(max=36) String teacherId,@RequestParam(required=false) String categoryId,@RequestParam(required=false) String courseId) {
        if (categoryId!=null || courseId!=null) { throw BusinessException.invalid("讲师列表不支持分类和课程筛选"); }
        return ApiResponse.ok(service.list(admin,page,pageSize,q,status,teacherId));
    }
    @GetMapping("/admin/content/teachers/{id}")
    @PreAuthorize("hasAuthority('content:read')")
    ApiResponse<AdminTeacher> get(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable @Size(max=36) String id) {
        return ApiResponse.ok(service.get(admin,id));
    }
    @PostMapping("/admin/content/teachers")
    @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<AdminTeacher> create(@AuthenticationPrincipal AdminPrincipal admin,
        @RequestHeader("Idempotency-Key") @Size(max=36) String key,@Valid @RequestBody Input input) {
        return ApiResponse.ok(service.create(admin,key,input));
    }
    @PutMapping("/admin/content/teachers/{id}")
    @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<AdminTeacher> update(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable @Size(max=36) String id,@Valid @RequestBody Update input) {
        return ApiResponse.ok(service.update(admin,id,input));
    }
    @DeleteMapping("/admin/content/teachers/{id}")
    @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<OK> archive(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable @Size(max=36) String id,@Valid @RequestBody CategoryModels.Archive input) {
        service.archive(admin,id,input);return ApiResponse.ok(new OK(true));
    }
}
