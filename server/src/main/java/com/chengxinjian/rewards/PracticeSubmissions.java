package com.chengxinjian.rewards;

import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.catalog.CategoryModels.Page;
import com.chengxinjian.common.*;
import com.chengxinjian.media.*;
import com.chengxinjian.user.UserPrincipal;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static com.chengxinjian.rewards.RewardQueries.*;

@RestController
@Validated
@RequestMapping("/api/v1")
@Transactional(isolation=Isolation.READ_COMMITTED)
public class PracticeSubmissions {
    private static final String BASE="/practice/submissions", ADMIN="/admin/submissions";
    record Input(@NotBlank @Size(max=36) String assetId,@NotBlank @Size(max=36) String practiceSessionId,@NotNull @Size(max=1000) String text) {}
    record Review(@NotNull @Pattern(regexp="approve|reject") String decision,@NotBlank @Size(max=500) String reason,@Positive @NotNull Integer version) {}
    record Submission(String id,int version,Instant createdAt,Instant updatedAt,String assetId,String practiceSessionId,
                      String text,String status,String reviewReason,List<String> rewardClaimIds) {}
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CreateSnapshot snapshots;
    private final ObjectMapper json;
    private final MediaStorage storage;
    private final AuditService audit;

    PracticeSubmissions(JdbcTemplate jdbc,Clock clock,CreateSnapshot snapshots,ObjectMapper json,MediaStorage storage,AuditService audit) {
        this.jdbc=jdbc;this.clock=clock;this.snapshots=snapshots;this.json=json;this.storage=storage;this.audit=audit;
    }
    private Submission row(ResultSet r,int n) throws SQLException {
        String id=r.getString("id");
        var claims=jdbc.queryForList("SELECT id FROM recording_reward_claim WHERE submission_id=? ORDER BY created_at,id",String.class,id);
        return new Submission(id,r.getInt("version"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),
            r.getString("asset_id"),r.getString("session_id"),r.getString("note"),r.getString("status"),r.getString("review_reason"),claims);
    }
    private Submission byId(String id) {
        return jdbc.query("SELECT * FROM practice_submission WHERE id=?",this::row,id).stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    @PostMapping(BASE)
    ApiResponse<JsonNode> submit(@AuthenticationPrincipal UserPrincipal user,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Input body) {
        return ApiResponse.ok(snapshots.user(user.id(),"POST "+BASE,key,body,()->{
            var session=jdbc.queryForList("SELECT status,credited_seconds FROM practice_session WHERE id=? AND user_id=? FOR UPDATE",
                body.practiceSessionId(),user.id()).stream().findFirst().orElseThrow(BusinessException::notFound);
            if (!"completed".equals(session.get("status")) || ((Number)session.get("credited_seconds")).intValue()==0)
                throw conflict("PRACTICE_NOT_COMPLETED","请先完成有有效时长的本人练习");
            var asset=jdbc.queryForList("SELECT status,purpose,sha256,duration_seconds FROM media_asset WHERE id=? AND owner_user_id=? FOR UPDATE",
                body.assetId(),user.id()).stream().findFirst().orElseThrow(BusinessException::notFound);
            if (!"practice_recording".equals(asset.get("purpose")) || !"ready".equals(asset.get("status"))
                    || !(asset.get("duration_seconds") instanceof Number duration) || duration.intValue()<1)
                throw conflict("RECORDING_NOT_READY","需要已完成技术校验的本人练习录制");
            var prior=jdbc.query("SELECT * FROM practice_submission WHERE user_id=? AND recording_hash=?",this::row,user.id(),asset.get("sha256"));
            if (!prior.isEmpty()) {
                var old=prior.getFirst();
                if (old.practiceSessionId().equals(body.practiceSessionId()) && old.assetId().equals(body.assetId()) && old.text().equals(body.text()))
                    return json.valueToTree(old);
                throw conflict("RECORDING_ALREADY_SUBMITTED","这份录制已经提交");
            }
            if (jdbc.queryForObject("SELECT COUNT(*) FROM practice_submission WHERE session_id=? AND status<>'rejected'",Integer.class,body.practiceSessionId())>0)
                throw conflict("PRACTICE_ALREADY_SUBMITTED","本次练习已有待审或已通过的录制");
            String id=UUID.randomUUID().toString();
            Timestamp now=Timestamp.from(clock.instant());
            jdbc.update("""
                INSERT INTO practice_submission(id,user_id,session_id,asset_id,recording_hash,note,status,version,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'pending',1,?,?)
                """,id,user.id(),body.practiceSessionId(),body.assetId(),asset.get("sha256"),body.text(),now,now);
            return json.valueToTree(byId(id));
        }));
    }
    @GetMapping(BASE+"/{id}")
    ApiResponse<Submission> detail(@AuthenticationPrincipal UserPrincipal user,@PathVariable String id) {
        return ApiResponse.ok(jdbc.query("SELECT * FROM practice_submission WHERE id=? AND user_id=?",this::row,id,user.id())
            .stream().findFirst().orElseThrow(BusinessException::notFound));
    }
    @GetMapping("/me/practice/submissions")
    ApiResponse<Page<Submission>> mine(@AuthenticationPrincipal UserPrincipal user,@RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(page(jdbc,"FROM practice_submission r WHERE r.user_id=?",List.of(user.id()),this::row,page,pageSize));
    }
    @GetMapping(ADMIN) @PreAuthorize("hasAuthority('moderation:review')")
    ApiResponse<Page<Submission>> queue(@AuthenticationPrincipal AdminPrincipal admin,@RequestParam(required=false) @Pattern(regexp="pending|approved|rejected") String status,
            @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        global(admin);
        return ApiResponse.ok(page(jdbc,"FROM practice_submission r"+(status==null?"":" WHERE r.status=?"),
            status==null?List.of():List.of(status),this::row,page,pageSize));
    }
    @PostMapping(ADMIN+"/{id}/preview") @PreAuthorize("hasAuthority('moderation:review')")
    ApiResponse<MediaModels.Preview> preview(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id) {
        global(admin);
        String key=jdbc.queryForList("""
            SELECT a.object_key FROM practice_submission s JOIN media_asset a ON a.id=s.asset_id AND a.owner_user_id=s.user_id
            WHERE s.id=? AND a.status='ready' AND a.purpose='practice_recording'
            """,String.class,id).stream().findFirst().orElseThrow(BusinessException::notFound);
        Instant expires=clock.instant().plusSeconds(120);
        var out=new MediaModels.Preview(storage.preview(key,expires),expires);
        audit.record(admin.id(),"submission.preview",id,null);
        return ApiResponse.ok(out);
    }
    @PostMapping(ADMIN+"/{id}/review") @PreAuthorize("hasAuthority('moderation:review')")
    ApiResponse<JsonNode> review(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Review body) {
        global(admin);
        return ApiResponse.ok(snapshots.admin(admin.id(),"POST "+ADMIN+"/"+id+"/review",key,body,ignored->global(admin),()->{
            var current=jdbc.query("SELECT * FROM practice_submission WHERE id=? FOR UPDATE",this::row,id)
                .stream().findFirst().orElseThrow(BusinessException::notFound);
            String status="approve".equals(body.decision())?"approved":"rejected";
            if (!"pending".equals(current.status())) {
                if (status.equals(current.status()) && body.reason().equals(current.reviewReason()) &&
                    (body.version()==current.version()-1 || body.version()==current.version())) return json.valueToTree(current);
                throw conflict("SUBMISSION_REVIEWED","录制已有最终审核结果");
            }
            if (body.version()!=current.version()) throw conflict("VERSION_CONFLICT","录制版本已变更");
            jdbc.update("UPDATE practice_submission SET status=?,review_reason=?,version=version+1,updated_at=? WHERE id=?",
                status,body.reason(),Timestamp.from(clock.instant()),id);
            audit.record(admin.id(),"submission."+status,id,body.reason());
            return json.valueToTree(byId(id));
        }));
    }
}
