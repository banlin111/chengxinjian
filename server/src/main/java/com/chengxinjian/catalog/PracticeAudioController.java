package com.chengxinjian.catalog;

import com.chengxinjian.auth.*;
import com.chengxinjian.common.*;
import com.chengxinjian.user.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
@Transactional(isolation=Isolation.READ_COMMITTED)
public class PracticeAudioController {
    private static final String ADMIN="/admin/content/{resource:meditations|noises}";
    record Input(@NotBlank @Size(max=200) String title,@Size(max=1000) String summary,@Size(max=36) String teacherId,
                 @NotBlank @Size(max=36) String coverAssetId,@NotBlank @Size(max=36) String categoryId,
                 @NotBlank @Size(max=36) String assetId,@NotNull @Pattern(regexp="free|member") String accessPolicy,
                 @NotNull @Pattern(regexp=CategoryModels.STATUS) String status,@Positive Integer version) {}
    private final PracticeAudioCatalog catalog;
    private final JdbcTemplate jdbc;
    private final CreateSnapshot snapshots;
    private final Clock clock;
    private final AuditService audit;

    PracticeAudioController(PracticeAudioCatalog catalog,JdbcTemplate jdbc,CreateSnapshot snapshots,Clock clock,AuditService audit) {
        this.catalog=catalog;this.jdbc=jdbc;this.snapshots=snapshots;this.clock=clock;this.audit=audit;
    }

    @GetMapping("/meditations")
    ApiResponse<CategoryModels.Page<ObjectNode>> meditations(@AuthenticationPrincipal UserPrincipal user,@Valid @ModelAttribute CourseModels.Filters filters,
            @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(catalog.list("meditation",user,null,filters,null,page,pageSize));
    }
    @GetMapping("/meditations/{id}")
    ApiResponse<ObjectNode> meditation(@AuthenticationPrincipal UserPrincipal user,@PathVariable String id) {
        return ApiResponse.ok(catalog.detail("meditation",id,user,null));
    }
    @GetMapping("/noises")
    ApiResponse<CategoryModels.Page<ObjectNode>> noises(@AuthenticationPrincipal UserPrincipal user,@RequestParam(required=false) String categoryId,
            @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(catalog.list("noise",user,null,null,new CourseModels.AdminFilters(null,null,categoryId,null,null),page,pageSize));
    }
    private static String kind(String resource) {return "noises".equals(resource)?"noise":"meditation";}
    @GetMapping(ADMIN) @PreAuthorize("hasAuthority('content:read')")
    ApiResponse<CategoryModels.Page<ObjectNode>> list(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String resource,@Valid @ModelAttribute CourseModels.AdminFilters filters,
            @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(catalog.list(kind(resource),null,admin,null,filters,page,pageSize));
    }
    @GetMapping(ADMIN+"/{id}") @PreAuthorize("hasAuthority('content:read')")
    ApiResponse<ObjectNode> detail(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String resource,@PathVariable String id) {
        return ApiResponse.ok(catalog.detail(kind(resource),id,null,admin));
    }
    @PostMapping(ADMIN) @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<JsonNode> create(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String resource,@RequestHeader("Idempotency-Key") String key,@RequestBody @Valid Input body) {
        if(body.version()!=null) throw BusinessException.invalid("创建音频不能指定版本");
        String kind=kind(resource);
        CourseCatalog.scope(admin,body.teacherId());
        return ApiResponse.ok(snapshots.admin(admin.id(),"POST /admin/content/"+resource,key,body,id->catalog.detail(kind,id,null,admin),
            ()->save(admin,kind,null,body)));
    }
    @PutMapping(ADMIN+"/{id}") @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<ObjectNode> update(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String resource,@PathVariable String id,@RequestBody @Valid Input body) {
        if(body.version()==null) throw BusinessException.invalid("更新需要版本");
        return ApiResponse.ok(save(admin,kind(resource),id,body));
    }
    private Map<String,Object> lock(String table,String id) {
        return jdbc.queryForList("SELECT * FROM "+table+" WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    private static BusinessException conflict(String code,String message) {return new BusinessException(HttpStatus.CONFLICT,code,message);}
    private Map<String,Object> existing(AdminPrincipal admin,String kind,String id) {
        var row=lock("practice_audio",id);
        if (!kind.equals(row.get("kind"))) throw BusinessException.notFound();
        CourseCatalog.scope(admin,(String)row.get("teacher_id"));
        return row;
    }
    private static void version(Map<String,Object> row,int version) {
        if (((Number)row.get("version")).intValue()!=version) throw conflict("VERSION_CONFLICT","内容已变更");
    }
    private Map<String,Object> asset(AdminPrincipal admin,String id,String purpose,Object previous) {
        var row=lock("media_asset",id);
        if (row.get("owner_admin_id")==null || (!"all".equals(admin.dataScope()) && !id.equals(previous) && !admin.id().equals(row.get("owner_admin_id"))))
            throw BusinessException.forbidden();
        if (!purpose.equals(row.get("purpose")) || !"ready".equals(row.get("status"))) throw conflict("ASSET_NOT_READY","素材用途或技术状态不符");
        return row;
    }
    private ObjectNode save(AdminPrincipal admin,String kind,String id,Input body) {
        boolean guided="meditation".equals(kind),create=id==null;
        if (guided ? body.summary()==null : body.summary()!=null || body.teacherId()!=null)
            throw BusinessException.invalid("音频字段与类型不匹配");
        CourseCatalog.scope(admin,body.teacherId());
        var old=create?Map.<String,Object>of():existing(admin,kind,id);
        if (!create) version(old,body.version());
        var category=lock("content_category",body.categoryId());
        boolean published="published".equals(body.status());
        if (!kind.equals(category.get("kind")) || !usable(category,published)) throw conflict("CONTENT_REFERENCE_INVALID","分类无效");
        if (body.teacherId()!=null && !usable(lock("content_teacher",body.teacherId()),published)) throw conflict("CONTENT_REFERENCE_INVALID","讲师状态无效");
        var cover=asset(admin,body.coverAssetId(),"cover",old.get("cover_asset_id"));
        var audio=asset(admin,body.assetId(),kind+"_media",old.get("asset_id"));
        if (!"approved".equals(cover.get("review_status")) || cover.get("public_url")==null
                || published && !"approved".equals(audio.get("review_status"))) throw conflict("ASSET_NOT_REVIEWED","请先审核封面和音频");
        if (!(audio.get("duration_seconds") instanceof Number seconds) || seconds.intValue()<1 || !audio.get("mime_type").toString().startsWith("audio/"))
            throw conflict("ASSET_NOT_READY","需要已验证时长的音频");
        var now=Timestamp.from(clock.instant());
        if (create) {
            id=UUID.randomUUID().toString();
            jdbc.update("""
                INSERT INTO practice_audio(id,kind,title,summary,category_id,teacher_id,cover_asset_id,asset_id,access_policy,status,version,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,1,?,?)
                """,id,kind,body.title(),body.summary(),body.categoryId(),body.teacherId(),body.coverAssetId(),body.assetId(),body.accessPolicy(),body.status(),now,now);
        } else jdbc.update("""
                UPDATE practice_audio SET title=?,summary=?,category_id=?,teacher_id=?,cover_asset_id=?,asset_id=?,access_policy=?,status=?,version=version+1,updated_at=? WHERE id=?
                """,body.title(),body.summary(),body.categoryId(),body.teacherId(),body.coverAssetId(),body.assetId(),body.accessPolicy(),body.status(),now,id);
        audit.record(admin.id(),"content."+kind+(create?".create":".update"),id,null);
        return catalog.detail(kind,id,null,admin);
    }
    private static boolean usable(Map<String,Object> parent,boolean published) {
        return !"archived".equals(parent.get("status")) && (!published || "published".equals(parent.get("status")));
    }
    @DeleteMapping(ADMIN+"/{id}") @PreAuthorize("hasAuthority('content:write')")
    ApiResponse<AdminAuthController.OK> archive(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String resource,@PathVariable String id,@Valid @RequestBody CategoryModels.Archive body) {
        String kind=kind(resource);
        var row=existing(admin,kind,id);
        if (!"archived".equals(row.get("status"))) {
            version(row,body.version());
            jdbc.update("UPDATE practice_audio SET status='archived',version=version+1,updated_at=? WHERE id=?",Timestamp.from(clock.instant()),id);
            audit.record(admin.id(),"content."+kind+".archive",id,body.reason());
        }
        return ApiResponse.ok(new AdminAuthController.OK(true));
    }
}
