package com.chengxinjian.catalog;

import com.chengxinjian.auth.AdminAuthController.OK;
import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.common.*;
import com.chengxinjian.user.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
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
public class Notices {
    public record Input(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=10000) String content,
                        @NotNull Instant publishedAt, @NotNull @Pattern(regexp=CategoryModels.STATUS) String status) {}
    public record Update(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=10000) String content,
                         @NotNull Instant publishedAt, @NotNull @Pattern(regexp=CategoryModels.STATUS) String status,
                         @NotNull @Positive Integer version) {
        Input input() { return new Input(title,content,publishedAt,status); }
    }
    public record Notice(String id,String title,String content,Instant publishedAt,boolean read) {}
    public record AdminNotice(String id,String title,String content,Instant publishedAt,String status,
                              int version,Instant createdAt,Instant updatedAt) {}
    private static final String BASE="/admin/content/notices";
    private static final String VISIBLE="n.status='published' AND n.published_at<=?";
    private static final String PUBLIC_SELECT="SELECT n.*,r.notice_version AS read_version FROM content_notice n "
        +"LEFT JOIN user_notice_read r ON r.notice_id=n.id AND r.user_id=? WHERE "+VISIBLE;
    private static final RowMapper<AdminNotice> ADMIN_ROW=(r,n)->new AdminNotice(r.getString("id"),r.getString("title"),
        r.getString("content"),r.getTimestamp("published_at").toInstant(),r.getString("status"),r.getInt("version"),
        r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
    private static final RowMapper<Notice> PUBLIC_ROW=(r,n)->new Notice(r.getString("id"),r.getString("title"),
        r.getString("content"),r.getTimestamp("published_at").toInstant(),r.getInt("read_version")==r.getInt("version"));
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectMapper json;
    private final AuditService audit;
    private final CreateSnapshot snapshots;

    public Notices(JdbcTemplate jdbc,Clock clock,ObjectMapper json,AuditService audit,CreateSnapshot snapshots) {
        this.jdbc=jdbc;this.clock=clock;this.json=json;this.audit=audit;this.snapshots=snapshots;
    }
    private static void global(AdminPrincipal admin) {
        if (!"all".equals(admin.dataScope())) throw BusinessException.forbidden();
    }
    private static void validate(Input input) {
        // Bound explicitly instead of allowing database date conversion errors to become 500s.
        if (input.publishedAt().isBefore(Instant.EPOCH) || input.publishedAt().isAfter(Instant.parse("9999-12-31T23:59:59Z")))
            throw BusinessException.invalid("发布时间需在 1970 至 9999 年之间");
    }
    private AdminNotice find(String id,boolean lock) {
        return jdbc.query("SELECT * FROM content_notice WHERE id=?"+(lock?" FOR UPDATE":""),ADMIN_ROW,id)
            .stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    private static void version(AdminNotice notice,int version) {
        if (notice.version()!=version) throw new BusinessException(HttpStatus.CONFLICT,"VERSION_CONFLICT","公告版本已变更");
    }
    public List<Notice> latest(UserPrincipal user,int limit) {
        return jdbc.query(PUBLIC_SELECT+" ORDER BY n.published_at DESC,n.id DESC LIMIT ?",PUBLIC_ROW,
            user==null?null:user.id(),Timestamp.from(clock.instant()),limit);
    }
    @GetMapping("/notices")
    public ApiResponse<CategoryModels.Page<Notice>> published(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        var now=Timestamp.from(clock.instant());
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM content_notice n WHERE "+VISIBLE,Long.class,now);
        var items=jdbc.query(PUBLIC_SELECT+" ORDER BY n.published_at DESC,n.id DESC LIMIT ? OFFSET ?",PUBLIC_ROW,
            user==null?null:user.id(),now,pageSize,((long)page-1)*pageSize);
        return ApiResponse.ok(new CategoryModels.Page<>(items,page,pageSize,total));
    }
    @GetMapping("/notices/{id}")
    public ApiResponse<Notice> detail(@AuthenticationPrincipal UserPrincipal user,@PathVariable String id) {
        return ApiResponse.ok(jdbc.query(PUBLIC_SELECT+" AND n.id=?",PUBLIC_ROW,
            user==null?null:user.id(),Timestamp.from(clock.instant()),id).stream().findFirst().orElseThrow(BusinessException::notFound));
    }
    @PutMapping("/me/notices/{id}/read")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<OK> read(@AuthenticationPrincipal UserPrincipal user,@PathVariable String id) {
        // Serialize with edits/archival and concurrent acknowledgements, including the first INSERT.
        var notice=find(id,true);
        if (!"published".equals(notice.status()) || notice.publishedAt().isAfter(clock.instant())) throw BusinessException.notFound();
        var rows=jdbc.queryForList("SELECT notice_version FROM user_notice_read WHERE user_id=? AND notice_id=?",user.id(),id);
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO user_notice_read(user_id,notice_id,notice_version,read_at) VALUES (?,?,?,?)",
                user.id(),id,notice.version(),Timestamp.from(clock.instant()));
        } else if (((Number)rows.getFirst().get("notice_version")).intValue()!=notice.version()) {
            jdbc.update("UPDATE user_notice_read SET notice_version=?,read_at=? WHERE user_id=? AND notice_id=?",
                notice.version(),Timestamp.from(clock.instant()),user.id(),id);
        }
        return ApiResponse.ok(new OK(true));
    }
    @GetMapping(BASE)
    @PreAuthorize("hasAuthority('content:read')")
    public ApiResponse<CategoryModels.Page<AdminNotice>> list(@AuthenticationPrincipal AdminPrincipal admin,
            @Valid @ModelAttribute CourseModels.AdminFilters filters,@RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        global(admin);
        if (filters.categoryId()!=null || filters.courseId()!=null || filters.teacherId()!=null)
            throw BusinessException.invalid("公告不支持课程、分类或讲师筛选");
        var where=new StringBuilder(" WHERE 1=1");var args=new ArrayList<Object>();
        if (filters.status()!=null) {where.append(" AND status=?");args.add(filters.status());}
        if (filters.q()!=null) {
            where.append(" AND title LIKE ? ESCAPE '!'");
            args.add("%"+filters.q().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        }
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM content_notice"+where,Long.class,args.toArray());
        args.add(pageSize);args.add(((long)page-1)*pageSize);
        return ApiResponse.ok(new CategoryModels.Page<>(jdbc.query("SELECT * FROM content_notice"+where
            +" ORDER BY published_at DESC,id DESC LIMIT ? OFFSET ?",ADMIN_ROW,args.toArray()),page,pageSize,total));
    }
    @GetMapping(BASE+"/{id}")
    @PreAuthorize("hasAuthority('content:read')")
    public ApiResponse<AdminNotice> get(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id) {
        global(admin);return ApiResponse.ok(find(id,false));
    }
    @PostMapping(BASE)
    @PreAuthorize("hasAuthority('content:write')")
    public ApiResponse<JsonNode> create(@AuthenticationPrincipal AdminPrincipal admin,@RequestHeader("Idempotency-Key") String key,
            @RequestBody @Valid Input input) {
        global(admin);validate(input);
        return ApiResponse.ok(snapshots.admin(admin.id(),"POST "+BASE,key,input,id->global(admin),()->{
            String id=UUID.randomUUID().toString();var now=Timestamp.from(clock.instant());
            jdbc.update("INSERT INTO content_notice(id,title,content,published_at,status,version,created_at,updated_at) VALUES (?,?,?,?,?,1,?,?)",
                id,input.title(),input.content(),Timestamp.from(input.publishedAt()),input.status(),now,now);
            audit.record(admin.id(),"notice.create",id,null);
            return json.valueToTree(find(id,false));
        }));
    }
    @PutMapping(BASE+"/{id}")
    @PreAuthorize("hasAuthority('content:write')")
    public ApiResponse<AdminNotice> update(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,
            @RequestBody @Valid Update input) {
        global(admin);version(find(id,true),input.version());validate(input.input());
        jdbc.update("UPDATE content_notice SET title=?,content=?,published_at=?,status=?,version=version+1,updated_at=? WHERE id=?",
            input.title(),input.content(),Timestamp.from(input.publishedAt()),input.status(),Timestamp.from(clock.instant()),id);
        audit.record(admin.id(),"notice.update",id,null);
        return ApiResponse.ok(find(id,false));
    }
    @DeleteMapping(BASE+"/{id}")
    @PreAuthorize("hasAuthority('content:write')")
    public ApiResponse<OK> archive(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,
            @RequestBody @Valid CategoryModels.Archive input) {
        global(admin);var notice=find(id,true);
        if (!"archived".equals(notice.status())) {
            version(notice,input.version());
            jdbc.update("UPDATE content_notice SET status='archived',version=version+1,updated_at=? WHERE id=?",Timestamp.from(clock.instant()),id);
            audit.record(admin.id(),"notice.archive",id,input.reason());
        }
        return ApiResponse.ok(new OK(true));
    }
}
