package com.chengxinjian.auth;

import com.chengxinjian.catalog.CategoryModels.Page;
import com.chengxinjian.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/admin")
@Transactional(isolation=Isolation.READ_COMMITTED)
public class AdminAccessManagement {
    public record RoleInput(@NotBlank @Size(max=64) String name,@NotNull @Size(max=100) List<@NotBlank @Size(max=64) String> permissions,
        @NotNull @Pattern(regexp="all|own_content") String dataScope) {}
    public record RoleUpdate(@NotBlank @Size(max=64) String name,@NotNull @Size(max=100) List<@NotBlank @Size(max=64) String> permissions,
        @NotNull @Pattern(regexp="all|own_content") String dataScope,@NotNull @Positive Integer version) {}
    public record AccountInput(@NotNull @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_.@-]{2,63}") String username,
        @NotBlank @Size(max=64) String displayName,@NotNull @Size(min=1,max=30) List<@NotBlank @Size(max=36) String> roleIds,
        @Size(max=36) String teacherId,@NotNull Boolean enabled,@NotBlank @Size(min=12,max=64) String initialPassword) {
        @Override public String toString() {return "AccountInput[redacted]";}
    }
    public record AccountUpdate(@NotNull @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_.@-]{2,63}") String username,
        @NotBlank @Size(max=64) String displayName,@NotNull @Size(min=1,max=30) List<@NotBlank @Size(max=36) String> roleIds,
        @Size(max=36) String teacherId,@NotNull Boolean enabled,@NotNull @Positive Integer version) {}
    public record Reset(@NotBlank @Size(min=12,max=64) String newPassword,@NotBlank @Size(max=500) String reason) {
        @Override public String toString() {return "Reset[redacted]";}
    }
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    org.springframework.http.ResponseEntity<?> duplicateName(org.springframework.dao.DuplicateKeyException ignored) {
        return org.springframework.http.ResponseEntity.status(409).body(ApiResponse.error("ALREADY_EXISTS","账号名或角色名已存在",null));
    }
    private final JdbcTemplate jdbc;private final AdminRepository admins;private final ObjectMapper json;
    private final AuditService audit;private final CreateSnapshot snapshots;private final PasswordEncoder passwords;private final Clock clock;
    public AdminAccessManagement(JdbcTemplate jdbc,AdminRepository admins,ObjectMapper json,AuditService audit,CreateSnapshot snapshots,PasswordEncoder passwords,Clock clock) {
        this.jdbc=jdbc;this.admins=admins;this.json=json;this.audit=audit;this.snapshots=snapshots;this.passwords=passwords;this.clock=clock;
    }
    private AdminPrincipal allowed(AdminPrincipal actor,String permission,boolean lock) {
        if(lock) jdbc.queryForObject("SELECT id FROM admin_security_control WHERE id=1 FOR UPDATE",Integer.class);
        var current=admins.principal(actor.id());
        if(!"all".equals(current.dataScope()) || !current.permissions().contains(permission)) throw BusinessException.forbidden();
        return current;
    }
    private BusinessException conflict(String code,String message) {return new BusinessException(HttpStatus.CONFLICT,code,message);}
    private void version(int expected,int actual) {if(expected!=actual)throw conflict("VERSION_CONFLICT","数据已修改，请重新读取");}
    private Timestamp now() {return Timestamp.from(clock.instant());}
    private ObjectNode base(ResultSet r) throws SQLException {
        return json.createObjectNode().put("id",r.getString("id")).put("version",r.getInt("version"))
            .put("createdAt",r.getTimestamp("created_at").toInstant().toString()).put("updatedAt",r.getTimestamp("updated_at").toInstant().toString());
    }
    private ObjectNode role(String id) {
        return jdbc.query("SELECT * FROM admin_role WHERE id=?",(r,n)->{
            var node=base(r).put("name",r.getString("name")).put("dataScope",r.getString("data_scope"));
            node.set("permissions",json.valueToTree(jdbc.queryForList("SELECT permission_code FROM admin_role_permission WHERE role_id=? ORDER BY permission_code",String.class,id)));return node;
        },id).stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    private ObjectNode account(String id) {
        return jdbc.query("SELECT id,username,display_name,teacher_id,enabled,version,created_at,updated_at FROM admin_account WHERE id=?",(r,n)->{
            var node=base(r).put("username",r.getString("username")).put("displayName",r.getString("display_name")).put("teacherId",r.getString("teacher_id")).put("enabled",r.getBoolean("enabled"));
            node.set("roleIds",json.valueToTree(jdbc.queryForList("SELECT role_id FROM admin_account_role WHERE account_id=? ORDER BY role_id",String.class,id)));return node;
        },id).stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    private void permissions(AdminPrincipal actor,List<String> values,String scope) {
        if(new HashSet<>(values).size()!=values.size()) throw BusinessException.invalid("权限不能重复");
        var dictionary=jdbc.queryForList("SELECT code FROM permission",String.class);
        if(!dictionary.containsAll(values)) throw BusinessException.invalid("权限不在字典中");
        if(!actor.permissions().containsAll(values)) throw BusinessException.forbidden();
        if("own_content".equals(scope) && !Set.of("content:read","content:write").containsAll(values))
            throw BusinessException.invalid("本人内容角色只能分配已支持范围隔离的内容权限");
    }
    private String roles(AdminPrincipal actor,List<String> ids,String teacher) {
        if(new HashSet<>(ids).size()!=ids.size())throw BusinessException.invalid("角色不能重复");
        String scope="own_content";
        for(String id:ids) {
            var role=role(id);List<String> perms=new ArrayList<>();role.get("permissions").forEach(v->perms.add(v.asText()));
            if(!actor.permissions().containsAll(perms))throw BusinessException.forbidden();
            if("all".equals(role.get("dataScope").asText()))scope="all";
        }
        if("own_content".equals(scope) && teacher==null)throw BusinessException.invalid("本人内容角色必须绑定讲师");
        if(teacher!=null && jdbc.queryForObject("SELECT COUNT(*) FROM content_teacher WHERE id=? AND status<>'archived'",Long.class,teacher)==0)throw BusinessException.invalid("讲师不存在或已归档");
        return scope;
    }
    private void assign(String id,List<String> roles) {
        jdbc.update("DELETE FROM admin_account_role WHERE account_id=?",id);
        for(String role:roles)jdbc.update("INSERT INTO admin_account_role(account_id,role_id) VALUES (?,?)",id,role);
    }
    private void lastAdministrator() {
        long count=jdbc.queryForObject("SELECT COUNT(DISTINCT a.id) FROM admin_account a JOIN admin_account_role ar ON ar.account_id=a.id JOIN admin_role_permission rp ON rp.role_id=ar.role_id WHERE a.enabled=TRUE AND a.data_scope='all' AND rp.permission_code='rbac:write' AND EXISTS (SELECT 1 FROM admin_account_role ar2 JOIN admin_role_permission rp2 ON rp2.role_id=ar2.role_id WHERE ar2.account_id=a.id AND rp2.permission_code='rbac:read')",Long.class);
        if(count==0)throw conflict("LAST_ADMIN_PROTECTED","必须保留至少一个启用的全局权限管理员");
    }
    @GetMapping("/permissions") @PreAuthorize("hasAuthority('rbac:read')")
    ApiResponse<List<Map<String,Object>>> permissions(@AuthenticationPrincipal AdminPrincipal actor) {
        allowed(actor,"rbac:read",false);return ApiResponse.ok(jdbc.queryForList("SELECT code,name,module FROM permission ORDER BY module,code"));
    }
    @GetMapping("/roles") @PreAuthorize("hasAuthority('rbac:read')")
    ApiResponse<List<ObjectNode>> roles(@AuthenticationPrincipal AdminPrincipal actor) {
        allowed(actor,"rbac:read",false);return ApiResponse.ok(jdbc.queryForList("SELECT id FROM admin_role ORDER BY name,id",String.class).stream().map(this::role).toList());
    }
    @PostMapping("/roles") @PreAuthorize("hasAuthority('rbac:write')")
    ApiResponse<JsonNode> createRole(@AuthenticationPrincipal AdminPrincipal actor,@RequestHeader("Idempotency-Key") String key,@RequestBody @Valid RoleInput input) {
        var current=allowed(actor,"rbac:write",true);permissions(current,input.permissions(),input.dataScope());
        return ApiResponse.ok(snapshots.admin(actor.id(),"admin.role.create",key,input,id->role(id),()->{
            String id=UUID.randomUUID().toString();var now=now();
            jdbc.update("INSERT INTO admin_role(id,name,data_scope,version,created_at,updated_at) VALUES (?,?,?,1,?,?)",id,input.name(),input.dataScope(),now,now);
            for(String p:input.permissions())jdbc.update("INSERT INTO admin_role_permission(role_id,permission_code) VALUES (?,?)",id,p);
            audit.record(actor.id(),"admin.role.create",id,null);return role(id);
        }));
    }
    @PutMapping("/roles/{id}") @PreAuthorize("hasAuthority('rbac:write')")
    ApiResponse<ObjectNode> updateRole(@AuthenticationPrincipal AdminPrincipal actor,@PathVariable String id,@RequestBody @Valid RoleUpdate input) {
        var current=allowed(actor,"rbac:write",true);permissions(current,input.permissions(),input.dataScope());
        var old=role(id);version(input.version(),old.get("version").asInt());
        // Cannot edit a role carrying powers the operator does not possess.
        old.get("permissions").forEach(p->{if(!current.permissions().contains(p.asText()))throw BusinessException.forbidden();});
        jdbc.update("UPDATE admin_role SET name=?,data_scope=?,version=version+1,updated_at=? WHERE id=?",input.name(),input.dataScope(),now(),id);
        jdbc.update("DELETE FROM admin_role_permission WHERE role_id=?",id);
        for(String p:input.permissions())jdbc.update("INSERT INTO admin_role_permission(role_id,permission_code) VALUES (?,?)",id,p);
        for(String accountId:jdbc.queryForList("SELECT account_id FROM admin_account_role WHERE role_id=? ORDER BY account_id",String.class,id)) {
            var a=account(accountId);var ids=new ArrayList<String>();a.get("roleIds").forEach(v->ids.add(v.asText()));
            String scope=roles(current,ids,a.get("teacherId").isNull()?null:a.get("teacherId").asText());
            jdbc.update("UPDATE admin_account SET data_scope=?,version=version+1,updated_at=? WHERE id=?",scope,now(),accountId);
        }
        lastAdministrator();audit.record(actor.id(),"admin.role.update",id,null);return ApiResponse.ok(role(id));
    }
    @DeleteMapping("/roles/{id}") @PreAuthorize("hasAuthority('rbac:write')")
    ApiResponse<AdminAuthController.OK> deleteRole(@AuthenticationPrincipal AdminPrincipal actor,@PathVariable String id) {
        var current=allowed(actor,"rbac:write",true);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM admin_role WHERE id=?",Long.class,id)==0)return ApiResponse.ok(new AdminAuthController.OK(true));
        role(id).get("permissions").forEach(p->{if(!current.permissions().contains(p.asText()))throw BusinessException.forbidden();});
        if(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account_role WHERE role_id=?",Long.class,id)>0)throw conflict("ROLE_IN_USE","角色仍绑定账号");
        jdbc.update("DELETE FROM admin_role_permission WHERE role_id=?",id);jdbc.update("DELETE FROM admin_role WHERE id=?",id);
        audit.record(actor.id(),"admin.role.delete",id,null);return ApiResponse.ok(new AdminAuthController.OK(true));
    }
    @GetMapping("/accounts") @PreAuthorize("hasAuthority('rbac:read')")
    ApiResponse<Page<ObjectNode>> accounts(@AuthenticationPrincipal AdminPrincipal actor,@RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        allowed(actor,"rbac:read",false);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM admin_account",Long.class);
        var items=jdbc.queryForList("SELECT id FROM admin_account ORDER BY created_at DESC,id LIMIT ? OFFSET ?",String.class,pageSize,((long)page-1)*pageSize).stream().map(this::account).toList();
        return ApiResponse.ok(new Page<>(items,page,pageSize,total));
    }
    @PostMapping("/accounts") @PreAuthorize("hasAuthority('rbac:write')")
    ApiResponse<JsonNode> createAccount(@AuthenticationPrincipal AdminPrincipal actor,@RequestHeader("Idempotency-Key") String key,@RequestBody @Valid AccountInput input) {
        var current=allowed(actor,"rbac:write",true);AdminInitialization.password(input.initialPassword());String scope=roles(current,input.roleIds(),input.teacherId());
        return ApiResponse.ok(snapshots.adminSensitive(actor.id(),"admin.account.create",key,input,id->account(id),()->{
            String id=UUID.randomUUID().toString();var now=now();
            jdbc.update("INSERT INTO admin_account(id,username,display_name,password_hash,enabled,data_scope,teacher_id,version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,1,?,?)",
                id,input.username(),input.displayName(),passwords.encode(input.initialPassword()),input.enabled(),scope,input.teacherId(),now,now);
            assign(id,input.roleIds());audit.record(actor.id(),"admin.account.create",id,null);return account(id);
        }));
    }
    private void targetPowers(AdminPrincipal actor,String id) {
        var perms=jdbc.queryForList("SELECT DISTINCT rp.permission_code FROM admin_account_role ar JOIN admin_role_permission rp ON rp.role_id=ar.role_id WHERE ar.account_id=?",String.class,id);
        if(!actor.permissions().containsAll(perms))throw BusinessException.forbidden();
    }
    @PutMapping("/accounts/{id}") @PreAuthorize("hasAuthority('rbac:write')")
    ApiResponse<ObjectNode> updateAccount(@AuthenticationPrincipal AdminPrincipal actor,@PathVariable String id,@RequestBody @Valid AccountUpdate input) {
        var current=allowed(actor,"rbac:write",true);var old=account(id);version(input.version(),old.get("version").asInt());targetPowers(current,id);
        String scope=roles(current,input.roleIds(),input.teacherId());
        jdbc.update("UPDATE admin_account SET username=?,display_name=?,enabled=?,data_scope=?,teacher_id=?,version=version+1,updated_at=? WHERE id=?",
            input.username(),input.displayName(),input.enabled(),scope,input.teacherId(),now(),id);
        assign(id,input.roleIds());lastAdministrator();
        // Disable/re-enable must not resurrect a previously issued session.
        if(!input.enabled())jdbc.update("UPDATE auth_session SET revoked=TRUE WHERE account_id=?",id);
        audit.record(actor.id(),"admin.account.update",id,null);return ApiResponse.ok(account(id));
    }
    @PostMapping("/accounts/{id}/reset-password") @PreAuthorize("hasAuthority('rbac:write')")
    ApiResponse<AdminAuthController.OK> reset(@AuthenticationPrincipal AdminPrincipal actor,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody @Valid Reset input) {
        var current=allowed(actor,"rbac:write",true);account(id);targetPowers(current,id);AdminInitialization.password(input.newPassword());
        snapshots.adminSensitive(actor.id(),"admin.account.password:"+id,key,input,ignored->account(id),()->{
            jdbc.update("UPDATE admin_account SET password_hash=?,version=version+1,updated_at=? WHERE id=?",passwords.encode(input.newPassword()),now(),id);
            jdbc.update("UPDATE auth_session SET revoked=TRUE WHERE account_id=?",id);audit.record(actor.id(),"admin.account.password",id,input.reason());
            return json.createObjectNode().put("id",id).put("success",true);
        });return ApiResponse.ok(new AdminAuthController.OK(true));
    }
    @GetMapping("/audit-logs") @PreAuthorize("hasAuthority('audit:read')")
    ApiResponse<Page<ObjectNode>> audit(@AuthenticationPrincipal AdminPrincipal actor,@RequestParam LocalDate from,@RequestParam LocalDate to,
        @RequestParam(required=false) @Size(max=36) String operatorId,@RequestParam(required=false) @Size(max=64) String action,
        @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        allowed(actor,"audit:read",false);if(from.isAfter(to)||from.plusDays(366).isBefore(to))throw BusinessException.invalid("审计查询日期范围无效或超过 366 天");
        var zone=ZoneId.of("Asia/Shanghai");var args=new ArrayList<Object>(List.of(Timestamp.from(from.atStartOfDay(zone).toInstant()),Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant())));
        String where=" FROM audit_log WHERE occurred_at>=? AND occurred_at<?";
        if(operatorId!=null){where+=" AND operator_id=?";args.add(operatorId);}if(action!=null){where+=" AND action=?";args.add(action);}
        long total=jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,args.toArray());args.add(pageSize);args.add(((long)page-1)*pageSize);
        var items=jdbc.query("SELECT *"+where+" ORDER BY occurred_at DESC,id LIMIT ? OFFSET ?",(r,n)->json.createObjectNode().put("id",r.getString("id")).put("operatorId",r.getString("operator_id"))
            .put("action",r.getString("action")).put("targetId",r.getString("target_id")).put("reason",r.getString("reason")).put("requestId",r.getString("request_id")).put("at",r.getTimestamp("occurred_at").toInstant().toString()),args.toArray());
        return ApiResponse.ok(new Page<>(items,page,pageSize,total));
    }
}
