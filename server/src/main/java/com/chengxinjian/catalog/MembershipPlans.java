package com.chengxinjian.catalog;

import com.chengxinjian.auth.AdminAuthController.OK;
import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.common.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
@Transactional(isolation=Isolation.READ_COMMITTED)
public class MembershipPlans {
    public record Input(@NotBlank @Size(max=100) String name, @NotNull @Pattern(regexp="month|quarter|year|lifetime") String period,
        @JsonProperty(required=true) @Positive Integer durationDays, @NotNull @PositiveOrZero Integer priceFen,
        @JsonProperty(required=true) @PositiveOrZero Integer originalPriceFen, @NotNull @Size(max=50) List<@NotBlank @Size(max=300) String> benefits,
        @NotNull @Pattern(regexp=CategoryModels.STATUS) String status) {}
    public record Update(@NotBlank @Size(max=100) String name, @NotNull @Pattern(regexp="month|quarter|year|lifetime") String period,
        @JsonProperty(required=true) @Positive Integer durationDays, @NotNull @PositiveOrZero Integer priceFen,
        @JsonProperty(required=true) @PositiveOrZero Integer originalPriceFen, @NotNull @Size(max=50) List<@NotBlank @Size(max=300) String> benefits,
        @NotNull @Pattern(regexp=CategoryModels.STATUS) String status, @NotNull @Positive Integer version) {
        Input input() { return new Input(name,period,durationDays,priceFen,originalPriceFen,benefits,status); }
    }
    public record Plan(String id,String name,String period,Integer durationDays,int priceFen,Integer originalPriceFen,
                       List<String> benefits,String status,int version,Instant createdAt,Instant updatedAt) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final AuditService audit;
    private final CreateSnapshot snapshots;
    private final RowMapper<Plan> row;
    private static final String BASE="/admin/content/membership-plans";
    public MembershipPlans(JdbcTemplate jdbc,ObjectMapper json,Clock clock,AuditService audit,CreateSnapshot snapshots) {
        this.jdbc=jdbc; this.json=json; this.clock=clock; this.audit=audit; this.snapshots=snapshots;
        row=(r,n)->new Plan(r.getString("id"),r.getString("name"),r.getString("period_kind"),(Integer)r.getObject("duration_days"),
            r.getInt("price_fen"),(Integer)r.getObject("original_price_fen"),benefits(r.getString("benefits_json")),r.getString("status"),
            r.getInt("version"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
    }
    private List<String> benefits(String text) {
        try { return List.of(json.readValue(text,String[].class)); }
        catch(java.io.IOException ex) { throw new IllegalStateException("Invalid plan benefits"); }
    }
    static void global(AdminPrincipal admin) {
        if (!"all".equals(admin.dataScope())) throw BusinessException.forbidden();
    }
    private void validate(Input input) {
        if ("lifetime".equals(input.period())!=(input.durationDays()==null))
            throw BusinessException.invalid("终身套餐时长应为空，其他套餐需明确天数");
        if (input.originalPriceFen()!=null && input.originalPriceFen()<input.priceFen())
            throw BusinessException.invalid("原价低于现价");
    }
    private Plan find(String id,boolean lock) {
        return jdbc.query("SELECT * FROM membership_plan WHERE id=?"+(lock?" FOR UPDATE":""),row,id).stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    private void version(Plan plan,int version) {
        if(plan.version()!=version) throw new BusinessException(HttpStatus.CONFLICT,"VERSION_CONFLICT","套餐版本已变更");
    }
    @GetMapping("/membership/plans")
    public ApiResponse<List<Plan>> published() {
        return ApiResponse.ok(jdbc.query("SELECT * FROM membership_plan WHERE status='published' ORDER BY price_fen,id",row));
    }
    @GetMapping(BASE)
    @PreAuthorize("hasAuthority('content:read')")
    public ApiResponse<CategoryModels.Page<Plan>> list(@AuthenticationPrincipal AdminPrincipal admin,@Valid @ModelAttribute CourseModels.AdminFilters filters,
            @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        global(admin);
        if (filters.courseId()!=null || filters.teacherId()!=null || filters.categoryId()!=null) throw BusinessException.invalid("套餐列表不支持内容关联筛选");
        var where=new StringBuilder(" WHERE 1=1"); var args=new ArrayList<Object>();
        if (filters.status()!=null) { where.append(" AND status=?"); args.add(filters.status()); }
        if (filters.q()!=null) {where.append(" AND name LIKE ? ESCAPE '!'"); args.add("%"+filters.q().replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM membership_plan"+where,Long.class,args.toArray());
        args.add(pageSize); args.add(((long)page-1)*pageSize);
        return ApiResponse.ok(new CategoryModels.Page<>(jdbc.query("SELECT * FROM membership_plan"+where+" ORDER BY created_at,id LIMIT ? OFFSET ?",row,args.toArray()),page,pageSize,total));
    }
    @GetMapping(BASE+"/{id}")
    @PreAuthorize("hasAuthority('content:read')")
    public ApiResponse<Plan> get(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id) { global(admin); return ApiResponse.ok(find(id,false)); }
    @PostMapping(BASE)
    @PreAuthorize("hasAuthority('content:write')")
    public ApiResponse<JsonNode> create(@AuthenticationPrincipal AdminPrincipal admin,@RequestHeader("Idempotency-Key") String key,@RequestBody @Valid Input input) {
        global(admin); validate(input);
        return ApiResponse.ok(snapshots.admin(admin.id(),"POST "+BASE,key,input,id->global(admin),()->{
            String id=UUID.randomUUID().toString(); var now=Timestamp.from(clock.instant());
            jdbc.update("INSERT INTO membership_plan(id,name,period_kind,duration_days,price_fen,original_price_fen,benefits_json,status,version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,1,?,?)",
                id,input.name(),input.period(),input.durationDays(),input.priceFen(),input.originalPriceFen(),json.valueToTree(input.benefits()).toString(),input.status(),now,now);
            audit.record(admin.id(),"membership.plan.create",id,null);
            return json.valueToTree(find(id,false));
        }));
    }
    @PutMapping(BASE+"/{id}")
    @PreAuthorize("hasAuthority('content:write')")
    public ApiResponse<Plan> update(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,@RequestBody @Valid Update input) {
        global(admin); version(find(id,true),input.version()); validate(input.input());
        jdbc.update("UPDATE membership_plan SET name=?,period_kind=?,duration_days=?,price_fen=?,original_price_fen=?,benefits_json=?,status=?,updated_at=?,version=version+1 WHERE id=?",
            input.name(),input.period(),input.durationDays(),input.priceFen(),input.originalPriceFen(),json.valueToTree(input.benefits()).toString(),input.status(),Timestamp.from(clock.instant()),id);
        audit.record(admin.id(),"membership.plan.update",id,null);
        return ApiResponse.ok(find(id,false));
    }
    @DeleteMapping(BASE+"/{id}")
    @PreAuthorize("hasAuthority('content:write')")
    public ApiResponse<OK> archive(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,@RequestBody @Valid CategoryModels.Archive input) {
        global(admin); var plan=find(id,true);
        if (!"archived".equals(plan.status())) {
            version(plan,input.version());
            jdbc.update("UPDATE membership_plan SET status='archived',version=version+1,updated_at=? WHERE id=?",Timestamp.from(clock.instant()),id);
            audit.record(admin.id(),"membership.plan.archive",id,input.reason());
        }
        return ApiResponse.ok(new OK(true));
    }
}
