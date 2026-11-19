package com.chengxinjian.catalog;

import com.chengxinjian.auth.*;
import com.chengxinjian.common.*;
import com.chengxinjian.user.UserPrincipal;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static com.chengxinjian.catalog.MixPresets.*;

@RestController
@RequestMapping("/api/v1")
@Validated
@Transactional(isolation=Isolation.REPEATABLE_READ)
public class MixPresetController {
    private static final String MINE="/me/mix-presets", ADMIN="/admin/content/mix-presets";
    record Personal(@NotBlank @Size(max=200) String title,@NotNull @Size(min=1,max=5) List<@NotNull @Valid Track> tracks,
                    @Positive Integer version) {}
    record Editorial(@NotBlank @Size(max=200) String title,@NotNull @Size(min=1,max=5) List<@NotNull @Valid Track> tracks,
                     @NotNull @Pattern(regexp=CategoryModels.STATUS) String status,@Positive Integer version) {}
    record Revision(@NotNull @Positive Integer version) {}
    private final MixPresets presets;
    private final CreateSnapshot snapshots;
    private final ObjectMapper json;
    private final AuditService audit;
    MixPresetController(MixPresets presets,CreateSnapshot snapshots,ObjectMapper json,AuditService audit) {
        this.presets=presets; this.snapshots=snapshots; this.json=json; this.audit=audit;
    }

    @GetMapping("/mix-presets")
    ApiResponse<CategoryModels.Page<Preset>> published(@RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(presets.listPublic(page,pageSize));
    }
    @GetMapping(MINE)
    ApiResponse<CategoryModels.Page<Preset>> mine(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(presets.listOwned(user.id(),page,pageSize));
    }
    @GetMapping(MINE+"/{id}")
    ApiResponse<Preset> restore(@AuthenticationPrincipal UserPrincipal user,@PathVariable String id) {
        return ApiResponse.ok(presets.owned(user.id(),id,false));
    }
    @PostMapping(MINE)
    @Transactional(isolation=Isolation.READ_COMMITTED)
    ApiResponse<JsonNode> createPersonal(@AuthenticationPrincipal UserPrincipal user,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Personal body) {
        requireCreate(body.version());
        return ApiResponse.ok(snapshots.user(user.id(),"POST "+MINE,key,body,
            ()->json.valueToTree(presets.save(user.id(),null,body.title(),"draft",body.tracks()))));
    }
    @PutMapping(MINE+"/{id}")
    @Transactional(isolation=Isolation.READ_COMMITTED)
    ApiResponse<Preset> editPersonal(@AuthenticationPrincipal UserPrincipal user,@PathVariable String id,@Valid @RequestBody Personal body) {
        var old=presets.owned(user.id(),id,true);
        MixPresets.version(old,body.version());
        return ApiResponse.ok(presets.save(user.id(),old,body.title(),"draft",body.tracks()));
    }
    @DeleteMapping(MINE+"/{id}")
    ApiResponse<AdminAuthController.OK> removePersonal(@AuthenticationPrincipal UserPrincipal user,@PathVariable String id,@Valid @RequestBody Revision body) {
        presets.removeOwned(user.id(),id,body.version());
        return ApiResponse.ok(new AdminAuthController.OK(true));
    }

    @PreAuthorize("hasAuthority('content:read')") @GetMapping(ADMIN)
    ApiResponse<CategoryModels.Page<Preset>> adminList(@AuthenticationPrincipal AdminPrincipal admin,@Valid @ModelAttribute CourseModels.AdminFilters filters,
            @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        MembershipPlans.global(admin);
        return ApiResponse.ok(presets.listAdmin(filters,page,pageSize));
    }
    @PreAuthorize("hasAuthority('content:read')") @GetMapping(ADMIN+"/{id}")
    ApiResponse<Preset> adminDetail(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id) {
        MembershipPlans.global(admin);
        return ApiResponse.ok(presets.editorial(id,false));
    }
    @PreAuthorize("hasAuthority('content:write')") @PostMapping(ADMIN)
    @Transactional(isolation=Isolation.READ_COMMITTED)
    ApiResponse<JsonNode> adminCreate(@AuthenticationPrincipal AdminPrincipal admin,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Editorial body) {
        MembershipPlans.global(admin);
        requireCreate(body.version());
        return ApiResponse.ok(snapshots.admin(admin.id(),"POST "+ADMIN,key,body,id->MembershipPlans.global(admin),()->{
            var saved=presets.save(null,null,body.title(),body.status(),body.tracks());
            audit.record(admin.id(),"content.mix-preset.create",saved.id(),null);
            return json.valueToTree(saved);
        }));
    }
    @PreAuthorize("hasAuthority('content:write')") @PutMapping(ADMIN+"/{id}")
    @Transactional(isolation=Isolation.READ_COMMITTED)
    ApiResponse<Preset> adminEdit(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,@Valid @RequestBody Editorial body) {
        MembershipPlans.global(admin);
        var old=presets.editorial(id,true);
        MixPresets.version(old,body.version());
        var saved=presets.save(null,old,body.title(),body.status(),body.tracks());
        audit.record(admin.id(),"content.mix-preset.update",id,null);
        return ApiResponse.ok(saved);
    }
    @PreAuthorize("hasAuthority('content:write')") @DeleteMapping(ADMIN+"/{id}")
    ApiResponse<AdminAuthController.OK> adminArchive(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,@Valid @RequestBody CategoryModels.Archive body) {
        MembershipPlans.global(admin);
        var old=presets.editorial(id,true);
        if (!"archived".equals(old.status())) {
            MixPresets.version(old,body.version());
            presets.archive(id);
            audit.record(admin.id(),"content.mix-preset.archive",id,body.reason());
        }
        return ApiResponse.ok(new AdminAuthController.OK(true));
    }
    private static void requireCreate(Integer version) {
        if (version!=null) throw BusinessException.invalid("创建配方无需版本");
    }
}
