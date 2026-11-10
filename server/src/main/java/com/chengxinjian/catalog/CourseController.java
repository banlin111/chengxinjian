package com.chengxinjian.catalog;

import com.chengxinjian.auth.AdminAuthController.OK;
import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.common.ApiResponse;
import com.chengxinjian.user.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static com.chengxinjian.catalog.CourseModels.*;
import static com.chengxinjian.catalog.CategoryModels.Page;

@RestController
@Validated
@RequestMapping("/api/v1")
public class CourseController {
    private final CourseCatalog catalog;
    private final CourseEditor editor;
    private final Entitlements entitlements;
    public CourseController(CourseCatalog catalog, CourseEditor editor, Entitlements entitlements) {
        this.catalog=catalog; this.editor=editor; this.entitlements=entitlements;
    }
    @GetMapping("/courses")
    ApiResponse<Page<ObjectNode>> courses(@AuthenticationPrincipal UserPrincipal user, @Valid @ModelAttribute Filters filters,
            @RequestParam(defaultValue="1") @Min(1) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(catalog.courses(user,filters,page,pageSize));
    }
    @GetMapping("/courses/{id}")
    ApiResponse<ObjectNode> course(@AuthenticationPrincipal UserPrincipal user,@PathVariable String id) {
        return ApiResponse.ok(catalog.course(id,null,user));
    }
    @GetMapping("/courses/{id}/lessons")
    ApiResponse<Page<ObjectNode>> lessons(@AuthenticationPrincipal UserPrincipal user,@PathVariable String id,
            @RequestParam(defaultValue="1") @Min(1) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(catalog.lessons(id,user,page,pageSize));
    }
    @GetMapping("/me/entitlements")
    ApiResponse<Page<Entitlements.Grant>> grants(@AuthenticationPrincipal UserPrincipal user,@RequestParam(defaultValue="1") @Min(1) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(entitlements.page(user.id(),page,pageSize));
    }
    @GetMapping("/admin/content/courses")
    @PreAuthorize("hasAuthority('content:read')")
    ApiResponse<Page<ObjectNode>> listCourse(@AuthenticationPrincipal AdminPrincipal admin,@Valid @ModelAttribute AdminFilters filters,
            @RequestParam(defaultValue="1") @Min(1) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(catalog.adminList(admin,false,filters,page,pageSize));
    }
    @GetMapping("/admin/content/courses/{id}")
    @PreAuthorize("hasAuthority('content:read')")
    ApiResponse<ObjectNode> getCourse(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id) {
        return ApiResponse.ok(catalog.course(id,admin,null));
    }
    @PostMapping("/admin/content/courses")
    @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<JsonNode> createCourse(@AuthenticationPrincipal AdminPrincipal admin,@RequestHeader("Idempotency-Key") String key,
            @RequestBody @Valid CourseInput input) {
        return ApiResponse.ok(editor.createCourse(admin,key,input));
    }
    @PutMapping("/admin/content/courses/{id}")
    @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<JsonNode> updateCourse(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,@RequestBody @Valid CourseUpdate update) {
        return ApiResponse.ok(editor.saveCourse(admin,id,update.input(),update.version()));
    }
    @DeleteMapping("/admin/content/courses/{id}")
    @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<OK> archiveCourse(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,@RequestBody @Valid CategoryModels.Archive input) {
        editor.archive(admin,id,input,false);
        return ApiResponse.ok(new OK(true));
    }

    @GetMapping("/admin/content/lessons")
    @PreAuthorize("hasAuthority('content:read')")
    ApiResponse<Page<ObjectNode>> listLesson(@AuthenticationPrincipal AdminPrincipal admin,@Valid @ModelAttribute AdminFilters filters,
            @RequestParam(defaultValue="1") @Min(1) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(catalog.adminList(admin,true,filters,page,pageSize));
    }
    @GetMapping("/admin/content/lessons/{id}")
    @PreAuthorize("hasAuthority('content:read')")
    ApiResponse<ObjectNode> getLesson(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id) {
        return ApiResponse.ok(catalog.lesson(id,admin,null));
    }
    @PostMapping("/admin/content/lessons")
    @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<JsonNode> createLesson(@AuthenticationPrincipal AdminPrincipal admin,@RequestHeader("Idempotency-Key") String key,
            @RequestBody @Valid LessonInput input) {
        return ApiResponse.ok(editor.createLesson(admin,key,input));
    }
    @PutMapping("/admin/content/lessons/{id}")
    @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<JsonNode> updateLesson(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,@RequestBody @Valid LessonUpdate update) {
        return ApiResponse.ok(editor.saveLesson(admin,id,update.input(),update.version()));
    }
    @DeleteMapping("/admin/content/lessons/{id}")
    @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<OK> archiveLesson(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,@RequestBody @Valid CategoryModels.Archive input) {
        editor.archive(admin,id,input,true);
        return ApiResponse.ok(new OK(true));
    }
}
