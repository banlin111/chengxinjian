package com.chengxinjian.user;

import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.catalog.CategoryModels.Page;
import com.chengxinjian.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@Validated
@Transactional
public class AdminUsers {
    record Status(@NotNull @Positive Integer version,@NotBlank @Size(max=500) String reason,@NotNull @Pattern(regexp="active|banned") String status) {}
    record Roles(@NotEmpty @Size(max=2) List<@NotNull @Pattern(regexp="user|teacher") String> roles,
                 @NotBlank @Size(max=500) String reason,@Positive Integer version) {}
    private final JdbcTemplate jdbc;
    private final UserService users;
    private final ObjectMapper json;
    private final Clock clock;
    private final AuditService audit;
    AdminUsers(JdbcTemplate jdbc,UserService users,ObjectMapper json,Clock clock,AuditService audit) {
        this.jdbc=jdbc; this.users=users; this.json=json; this.clock=clock; this.audit=audit;
    }
    private void global(AdminPrincipal actor) {
        if (!"all".equals(actor.dataScope())) throw BusinessException.forbidden();
    }
    private ObjectNode detail(String id) {
        var rows=jdbc.query("SELECT version,created_at,updated_at,last_login_at FROM app_user WHERE id=?", (r,n)->{
            ObjectNode result=json.valueToTree(users.accountProfile(id));
            var registered=r.getTimestamp("created_at").toInstant().toString();
            return result.put("version",r.getInt("version")).put("registeredAt",registered).put("createdAt",registered)
                .put("updatedAt",r.getTimestamp("updated_at").toInstant().toString())
                .put("lastLoginAt",r.getTimestamp("last_login_at").toInstant().toString())
                .put("totalPracticeSeconds",0).put("streakDays",0);
        },id);
        return rows.stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('user_directory:read')")
    ApiResponse<ObjectNode> get(@AuthenticationPrincipal AdminPrincipal actor,@PathVariable String id) {
        global(actor); return ApiResponse.ok(detail(id));
    }
    @GetMapping @PreAuthorize("hasAuthority('user_directory:read')")
    ApiResponse<Page<ObjectNode>> list(@AuthenticationPrincipal AdminPrincipal actor,
            @RequestParam(required=false) @Pattern(regexp="1[3-9][0-9]{9}") String phone,
            @RequestParam(required=false) @Pattern(regexp="active|banned|deletion_pending") String status,
            @RequestParam(required=false) LocalDate registeredFrom,@RequestParam(required=false) LocalDate registeredTo,
            @RequestParam(required=false) Boolean membershipActive,@RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        global(actor);
        if (registeredFrom!=null && registeredTo!=null && registeredFrom.isAfter(registeredTo)) throw BusinessException.invalid("注册日期范围错误");
        var zone=ZoneId.of("Asia/Shanghai");
        var args=new ArrayList<Object>();
        String filter=" FROM app_user u WHERE 1=1";
        if (phone!=null) { filter+=" AND u.phone=?"; args.add(phone); }
        if (status!=null) { filter+=" AND u.status=?"; args.add(status); }
        if (registeredFrom!=null) { filter+=" AND u.created_at>=?"; args.add(Timestamp.from(registeredFrom.atStartOfDay(zone).toInstant())); }
        if (registeredTo!=null) { filter+=" AND u.created_at<?"; args.add(Timestamp.from(registeredTo.plusDays(1).atStartOfDay(zone).toInstant())); }
        if (membershipActive!=null) {
            filter+=membershipActive ? " AND EXISTS" : " AND NOT EXISTS";
            filter+=" (SELECT 1 FROM entitlement_grant g WHERE g.user_id=u.id AND kind='membership' AND revoked_at IS NULL AND starts_at<=? AND (expires_at IS NULL OR expires_at>?))";
            var time=Timestamp.from(clock.instant()); args.add(time); args.add(time);
        }
        long total=jdbc.queryForObject("SELECT COUNT(*)"+filter,Long.class,args.toArray());
        args.add(pageSize); args.add(((long)page-1)*pageSize);
        var ids=jdbc.queryForList("SELECT u.id"+filter+" ORDER BY u.created_at DESC,u.id LIMIT ? OFFSET ?",String.class,args.toArray());
        return ApiResponse.ok(new Page<>(ids.stream().map(this::detail).toList(),page,pageSize,total));
    }
    private Map<String,Object> lock(String id,Integer version) {
        var row=jdbc.queryForList("SELECT status,version,teacher_role FROM app_user WHERE id=? FOR UPDATE",id)
            .stream().findFirst().orElseThrow(BusinessException::notFound);
        if (version!=null && version!=((Number)row.get("version")).intValue())
            throw new BusinessException(HttpStatus.CONFLICT,"VERSION_CONFLICT","用户资料已变更");
        if ("deletion_pending".equals(row.get("status")))
            throw new BusinessException(HttpStatus.CONFLICT,"ACCOUNT_UNAVAILABLE","注销流程中的账号不可在此修改");
        return row;
    }
    @PutMapping("/{id}/status") @PreAuthorize("hasAuthority('user_directory:write')")
    ApiResponse<ObjectNode> status(@AuthenticationPrincipal AdminPrincipal actor,@PathVariable String id,@Valid @RequestBody Status input) {
        global(actor);
        var row=lock(id,input.version());
        if (!input.status().equals(row.get("status"))) {
            jdbc.update("UPDATE app_user SET status=?,version=version+1,updated_at=? WHERE id=?",input.status(),Timestamp.from(clock.instant()),id);
            if ("banned".equals(input.status())) jdbc.update("UPDATE user_auth_session SET revoked=TRUE WHERE user_id=? AND revoked=FALSE",id);
            audit.record(actor.id(),"user.status."+input.status(),id,input.reason());
        }
        return ApiResponse.ok(detail(id));
    }
    @PutMapping("/{id}/roles") @PreAuthorize("hasAuthority('user_directory:write')")
    ApiResponse<ObjectNode> roles(@AuthenticationPrincipal AdminPrincipal actor,@PathVariable String id,@Valid @RequestBody Roles input) {
        global(actor);
        var row=lock(id,input.version());
        var distinct=new HashSet<>(input.roles());
        if (!distinct.contains("user") || distinct.size()!=input.roles().size()) throw BusinessException.invalid("须保留普通用户角色，角色不能重复");
        boolean teacher=distinct.contains("teacher");
        boolean linked=jdbc.queryForObject("SELECT COUNT(*) FROM content_teacher WHERE user_id=? AND status<>'archived'",Long.class,id)>0;
        if (!teacher && linked) throw new BusinessException(HttpStatus.CONFLICT,"TEACHER_LINKED","请先处理关联讲师资料");
        if (teacher!=((Boolean)row.get("teacher_role") || linked)) {
            jdbc.update("UPDATE app_user SET teacher_role=?,version=version+1,updated_at=? WHERE id=?",teacher,Timestamp.from(clock.instant()),id);
            audit.record(actor.id(),"user.roles",id,input.reason());
        }
        return ApiResponse.ok(detail(id));
    }
}
