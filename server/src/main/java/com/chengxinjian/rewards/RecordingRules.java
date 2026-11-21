package com.chengxinjian.rewards;

import com.chengxinjian.auth.*;
import com.chengxinjian.catalog.*;
import com.chengxinjian.common.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static com.chengxinjian.rewards.RewardQueries.*;

@RestController
@RequestMapping("/api/v1/admin/content/reward-rules")
@Validated
@Transactional(isolation=Isolation.READ_COMMITTED)
public class RecordingRules {
    record Input(@NotBlank @Size(max=200) String name,@NotNull @Pattern(regexp="submission") String trigger,Integer requiredDays,
                 @NotNull @Pattern(regexp="course") String kind,@NotBlank @Size(max=36) String courseId,String physicalName,
                 @NotBlank @Size(max=36) String coverAssetId,@PositiveOrZero Integer stock,@NotNull Instant startAt,Instant endAt,
                 @NotNull @Positive Integer perUserLimit,@NotNull Boolean requiresReview,@NotNull @Pattern(regexp=CategoryModels.STATUS) String status,
                 @Positive Integer version) {}
    record Rule(String id,int version,Instant createdAt,Instant updatedAt,String name,String trigger,Integer requiredDays,
                String kind,String courseId,String physicalName,String coverUrl,Integer stockRemaining,Instant startAt,Instant endAt,
                int perUserLimit,boolean requiresReview,String status) {}
    static final String VALID_COVER="SELECT id FROM media_asset WHERE purpose='cover' AND owner_admin_id IS NOT NULL AND status='ready' AND review_status='approved' AND public_url IS NOT NULL";
    static final String VISIBLE="r.status='published' AND r.cover_asset_id IN ("+VALID_COVER+") AND r.course_id IN ("+CourseCatalog.visibleCourseIds()+")";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CreateSnapshot snapshots;
    private final AuditService audit;
    private final Clock clock;
    private final boolean enabled;
    RecordingRules(JdbcTemplate jdbc,ObjectMapper json,CreateSnapshot snapshots,AuditService audit,Clock clock,
                   @Value("${RECORDING_REWARDS_ENABLED:false}") boolean enabled) {
        this.jdbc=jdbc;this.json=json;this.snapshots=snapshots;this.audit=audit;this.clock=clock;this.enabled=enabled;
    }
    boolean enabled() {return enabled;}
    Rule rule(ResultSet r,int n) throws SQLException {
        var cover=jdbc.queryForList("SELECT public_url FROM media_asset WHERE id=?",String.class,r.getString("cover_asset_id"));
        Timestamp end=r.getTimestamp("ends_at");
        Integer stock=r.getObject("stock",Integer.class);
        return new Rule(r.getString("id"),r.getInt("version"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),
            r.getString("name"),"submission",null,"course",r.getString("course_id"),null,Objects.toString(cover.getFirst(),""),
            stock==null?null:stock-r.getInt("issued"),r.getTimestamp("starts_at").toInstant(),end==null?null:end.toInstant(),
            r.getInt("per_user_limit"),true,r.getString("status"));
    }
    private ObjectNode adminRow(ResultSet r,int n) throws SQLException {
        ObjectNode out=json.valueToTree(rule(r,n));
        return out.put("coverAssetId",r.getString("cover_asset_id"));
    }
    private ObjectNode get(String id,boolean lock) {
        return jdbc.query("SELECT * FROM recording_reward_rule WHERE id=?"+(lock?" FOR UPDATE":""),this::adminRow,id)
            .stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    @PreAuthorize("hasAuthority('rewards:manage')")
    @GetMapping
    ApiResponse<CategoryModels.Page<ObjectNode>> list(@AuthenticationPrincipal AdminPrincipal admin,@Valid @ModelAttribute CourseModels.AdminFilters filters,
            @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        global(admin);
        if (filters.teacherId()!=null || filters.categoryId()!=null) throw BusinessException.invalid("奖励规则不支持此筛选");
        var args=new ArrayList<Object>();
        String from="FROM recording_reward_rule r WHERE 1=1";
        if (filters.status()!=null) {from+=" AND r.status=?";args.add(filters.status());}
        if (filters.courseId()!=null) {from+=" AND r.course_id=?";args.add(filters.courseId());}
        if (filters.q()!=null) {from+=" AND r.name LIKE ? ESCAPE '!'";args.add("%"+filters.q().replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
        return ApiResponse.ok(page(jdbc,from,args,this::adminRow,page,pageSize));
    }
    @PreAuthorize("hasAuthority('rewards:manage')")
    @GetMapping("/{id}")
    ApiResponse<ObjectNode> detail(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id) {
        global(admin);return ApiResponse.ok(get(id,false));
    }
    @PreAuthorize("hasAuthority('rewards:manage')")
    @PostMapping
    ApiResponse<JsonNode> create(@AuthenticationPrincipal AdminPrincipal admin,@RequestHeader("Idempotency-Key") String key,@RequestBody @Valid Input body) {
        global(admin);
        if (body.version()!=null) throw BusinessException.invalid("创建无需版本");
        return ApiResponse.ok(snapshots.admin(admin.id(),"POST /admin/content/reward-rules",key,body,id->global(admin),()->save(admin,null,body)));
    }
    @PreAuthorize("hasAuthority('rewards:manage')")
    @PutMapping("/{id}")
    ApiResponse<ObjectNode> update(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,@RequestBody @Valid Input body) {
        global(admin);return ApiResponse.ok(save(admin,id,body));
    }
    private ObjectNode save(AdminPrincipal admin,String id,Input b) {
        if (b.requiredDays()!=null || b.physicalName()!=null || !b.requiresReview() || b.endAt()!=null && !b.endAt().isAfter(b.startAt()))
            throw BusinessException.invalid("录制课程奖必须人工审核，且有效期正确");
        for (Instant date:new Instant[]{b.startAt(),b.endAt()}) {
            if(date!=null && (date.isBefore(Instant.parse("1000-01-01T00:00:00Z")) || date.isAfter(Instant.parse("9999-12-31T23:59:59Z"))))
                throw BusinessException.invalid("规则时间超出范围");
        }
        if ("published".equals(b.status()) && !enabled) throw conflict("REWARDS_DISABLED","录制奖励尚未获准启用");
        boolean create=id==null;
        if (!create) {
            var old=get(id,true);
            if (b.version()==null || b.version()!=old.path("version").asInt()) throw conflict("VERSION_CONFLICT","规则版本已变更");
            int issued=jdbc.queryForObject("SELECT issued FROM recording_reward_rule WHERE id=?",Integer.class,id);
            if (issued>0 && !old.path("courseId").asText().equals(b.courseId()) || b.stock()!=null && b.stock()<issued)
                throw conflict("REWARD_RULE_IN_USE","已发放的课程与库存不能被覆盖");
        }
        if (jdbc.queryForList("SELECT id FROM content_course WHERE id=?",String.class,b.courseId()).isEmpty()
                || jdbc.queryForList(VALID_COVER+" AND id=?",String.class,b.coverAssetId()).isEmpty()) throw BusinessException.notFound();
        if ("published".equals(b.status()) && jdbc.queryForList(CourseCatalog.visibleCourseIds()+" AND c.id=?",String.class,b.courseId()).isEmpty())
            throw conflict("COURSE_UNAVAILABLE","请先发布奖励课程");
        var now=Timestamp.from(clock.instant());
        var start=Timestamp.from(b.startAt());var end=b.endAt()==null?null:Timestamp.from(b.endAt());
        if (create) {
            id=UUID.randomUUID().toString();
            jdbc.update("""
                INSERT INTO recording_reward_rule(id,name,course_id,cover_asset_id,stock,starts_at,ends_at,per_user_limit,status,version,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,1,?,?)
                """,id,b.name(),b.courseId(),b.coverAssetId(),b.stock(),start,end,b.perUserLimit(),b.status(),now,now);
        } else jdbc.update("""
            UPDATE recording_reward_rule SET name=?,course_id=?,cover_asset_id=?,stock=?,starts_at=?,ends_at=?,per_user_limit=?,status=?,
                version=version+1,updated_at=? WHERE id=?
            """,b.name(),b.courseId(),b.coverAssetId(),b.stock(),start,end,b.perUserLimit(),b.status(),now,id);
        audit.record(admin.id(),"recording-rule."+b.status(),id,null);
        return get(id,false);
    }
    @PreAuthorize("hasAuthority('rewards:manage')")
    @DeleteMapping("/{id}")
    ApiResponse<AdminAuthController.OK> archive(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,@Valid @RequestBody CategoryModels.Archive b) {
        global(admin);var old=get(id,true);
        if (!"archived".equals(old.path("status").asText())) {
            if (b.version()!=old.path("version").asInt()) throw conflict("VERSION_CONFLICT","规则版本已变更");
            jdbc.update("UPDATE recording_reward_rule SET status='archived',version=version+1,updated_at=? WHERE id=?",Timestamp.from(clock.instant()),id);
            audit.record(admin.id(),"recording-rule.archive",id,b.reason());
        }
        return ApiResponse.ok(new AdminAuthController.OK(true));
    }
}
