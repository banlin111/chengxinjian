package com.chengxinjian.catalog;

import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.common.*;
import com.chengxinjian.user.*;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
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
public class AdminGrants {
    public record Input(@NotNull @Pattern(regexp="course|membership") String kind,@NotBlank @Size(max=36) String targetId,
                        @Positive Integer days,Boolean lifetime,@NotBlank @Size(max=500) String reason) {}
    public record Reason(@NotBlank @Size(max=500) String reason) {}
    public record Grant(String id,String userId,Entitlements.Grant entitlement,String reason,String operatorId,int version,Instant createdAt,Instant updatedAt) {}
    private static final String SELECT="SELECT g.id AS grant_id,g.operator_id,g.reason,g.version,g.created_at,g.updated_at,e.* FROM admin_grant g JOIN entitlement_grant e ON e.id=g.entitlement_id ";
    private final JdbcTemplate jdbc;
    private final CreateSnapshot snapshots;
    private final ObjectMapper json;
    private final UserService users;
    private final Entitlements entitlements;
    private final AuditService audit;
    private final Clock clock;
    public AdminGrants(JdbcTemplate jdbc,CreateSnapshot snapshots,ObjectMapper json,UserService users,Entitlements entitlements,AuditService audit,Clock clock) {
        this.jdbc=jdbc; this.snapshots=snapshots; this.json=json; this.users=users; this.entitlements=entitlements; this.audit=audit; this.clock=clock;
    }
    @GetMapping("/me/membership")
    public ApiResponse<UserModels.Membership> membership(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(entitlements.membership(user.id()));
    }
    private Grant row(java.sql.ResultSet r,int n) throws java.sql.SQLException {
        return new Grant(r.getString("grant_id"),r.getString("user_id"),entitlements.row(r,clock.instant()),r.getString("reason"),r.getString("operator_id"),
            r.getInt("version"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
    }
    private Grant find(String id,boolean lock) {
        return jdbc.query(SELECT+" WHERE g.id=?"+(lock?" FOR UPDATE":""),this::row,id).stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    @GetMapping("/admin/users/{id}/grants")
    @PreAuthorize("hasAuthority('users:read')")
    public ApiResponse<CategoryModels.Page<Grant>> grants(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,
            @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        MembershipPlans.global(admin);
        if (jdbc.queryForList("SELECT id FROM app_user WHERE id=?",String.class,id).isEmpty()) throw BusinessException.notFound();
        var rows=jdbc.query(SELECT+" WHERE e.user_id=? ORDER BY g.created_at DESC,g.id LIMIT ? OFFSET ?",this::row,id,pageSize,((long)page-1)*pageSize);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM admin_grant g JOIN entitlement_grant e ON e.id=g.entitlement_id WHERE e.user_id=?",Long.class,id);
        return ApiResponse.ok(new CategoryModels.Page<>(rows,page,pageSize,total));
    }
    @PostMapping("/admin/users/{id}/grants")
    @PreAuthorize("hasAuthority('users:write')")
    public ApiResponse<JsonNode> issue(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody @Valid Input input) {
        MembershipPlans.global(admin);
        return ApiResponse.ok(snapshots.admin(admin.id(),"POST /admin/users/"+id+"/grants",key,input,old->MembershipPlans.global(admin),()->{
            users.requireActive(id,true);
            boolean membership="membership".equals(input.kind());
            String planId=membership?input.targetId():null;
            Integer days=input.days();
            if (Boolean.TRUE.equals(input.lifetime()) && days!=null) throw BusinessException.invalid("永久授权不能同时指定天数");
            if (membership) {
                var plan=jdbc.queryForList("SELECT duration_days FROM membership_plan WHERE id=? AND status='published' FOR UPDATE",input.targetId())
                    .stream().findFirst().orElseThrow(BusinessException::notFound);
                if (input.days()==null && !Boolean.TRUE.equals(input.lifetime())) days=(Integer)plan.get("duration_days");
            } else {
                if(jdbc.queryForList("SELECT id FROM content_course WHERE id=? AND status='published' FOR UPDATE",String.class,input.targetId()).isEmpty())
                    throw BusinessException.notFound();
            }
            if (days==null && Boolean.FALSE.equals(input.lifetime())) throw BusinessException.invalid("限时授权须明确天数");
            Instant start=clock.instant(),end=days==null?null:start.plus(days,ChronoUnit.DAYS);
            if (end!=null && end.isAfter(Instant.parse("9999-12-31T23:59:59Z"))) throw BusinessException.invalid("到期日期超出当前数据库时间范围");
            String grant=UUID.randomUUID().toString(),entitlement=UUID.randomUUID().toString();
            Timestamp now=Timestamp.from(start);
            jdbc.update("INSERT INTO entitlement_grant(id,user_id,kind,target_id,course_id,source,source_id,starts_at,expires_at) VALUES (?,?,?,?,?,'admin',?,?,?)",
                entitlement,id,input.kind(),input.targetId(),membership?null:input.targetId(),grant,now,end==null?null:Timestamp.from(end));
            jdbc.update("INSERT INTO admin_grant(id,entitlement_id,operator_id,plan_id,reason,version,created_at,updated_at) VALUES (?,?,?,?,?,1,?,?)",
                grant,entitlement,admin.id(),planId,input.reason(),now,now);
            audit.record(admin.id(),"users.grant.issue",grant,input.reason());
            return json.valueToTree(find(grant,false));
        }));
    }
    @PostMapping("/admin/grants/{id}/revoke")
    @PreAuthorize("hasAuthority('users:write')")
    public ApiResponse<JsonNode> revoke(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody @Valid Reason body) {
        MembershipPlans.global(admin);
        return ApiResponse.ok(snapshots.admin(admin.id(),"POST /admin/grants/"+id+"/revoke",key,body,old->MembershipPlans.global(admin),()->{
            var grant=find(id,true); var now=Timestamp.from(clock.instant());
            if (!"revoked".equals(grant.entitlement().status())) {
                jdbc.update("UPDATE entitlement_grant SET revoked_at=? WHERE id=?",now,grant.entitlement().id());
                jdbc.update("UPDATE admin_grant SET updated_at=?,version=version+1 WHERE id=?",now,id);
                audit.record(admin.id(),"users.grant.revoke",id,body.reason());
            }
            return json.valueToTree(find(id,false));
        }));
    }
}
