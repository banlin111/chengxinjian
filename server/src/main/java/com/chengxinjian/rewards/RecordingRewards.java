package com.chengxinjian.rewards;

import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.catalog.CategoryModels.Page;
import com.chengxinjian.common.*;
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
@RequestMapping("/api/v1")
@Validated
@Transactional(isolation=Isolation.READ_COMMITTED)
public class RecordingRewards {
    record Request(@NotBlank @Size(max=36) String ruleId,@NotBlank @Size(max=36) String submissionId,@Null String addressId) {}
    record Eligibility(RecordingRules.Rule rule,boolean eligible,int progressDays,String reason,String claimId) {}
    record Claim(String id,int version,Instant createdAt,Instant updatedAt,String ruleId,int ruleVersion,String submissionId,
                 String kind,String status,String orderId,String shipmentId,String addressId,String rejectionReason) {}
    record Evidence(String id,String sessionId,String status,Instant reviewed) {}
    private final JdbcTemplate jdbc;
    private final RecordingRules rules;
    private final Clock clock;
    private final CreateSnapshot snapshots;
    private final ObjectMapper json;
    RecordingRewards(JdbcTemplate jdbc,RecordingRules rules,Clock clock,CreateSnapshot snapshots,ObjectMapper json) {
        this.jdbc=jdbc;this.rules=rules;this.clock=clock;this.snapshots=snapshots;this.json=json;
    }
    private Claim claimRow(ResultSet r,int n) throws SQLException {
        var time=r.getTimestamp("created_at").toInstant();
        return new Claim(r.getString("id"),1,time,time,r.getString("rule_id"),r.getInt("rule_version"),r.getString("submission_id"),
            "course","issued",null,null,null,null);
    }
    private Evidence evidence(String actor,String id) {
        return jdbc.query("SELECT id,session_id,status,updated_at FROM practice_submission WHERE user_id=? AND id=?",
            (r,n)->new Evidence(id,r.getString("session_id"),r.getString("status"),r.getTimestamp("updated_at").toInstant()),actor,id)
            .stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    private Eligibility eligibility(String actor,RecordingRules.Rule rule,Evidence selected) {
        var args=new ArrayList<Object>(List.of(actor,rule.courseId()));
        String used="SELECT id FROM recording_reward_claim WHERE user_id=? AND (course_id=?";
        if (selected!=null) {used+=" OR submission_id=?";args.add(selected.id());}
        var existing=jdbc.queryForList(used+") ORDER BY created_at,id LIMIT 1",String.class,args.toArray());
        if (!existing.isEmpty()) return new Eligibility(rule,false,0,"课程或录制已领奖",existing.getFirst());
        var now=Timestamp.from(clock.instant());
        var owned=jdbc.queryForList("""
            SELECT id FROM entitlement_grant WHERE user_id=? AND course_id=? AND revoked_at IS NULL
            AND starts_at<=? AND (expires_at IS NULL OR expires_at>?) LIMIT 1
            """,String.class,actor,rule.courseId(),now,now);
        String reason=null;
        if (!owned.isEmpty()) return new Eligibility(rule,false,0,"已拥有该课程权益",null);
        if (rule.stockRemaining()!=null && rule.stockRemaining()==0) reason="奖励库存已用完";
        else if (selected!=null) {
            if (!"approved".equals(selected.status())) reason="录制尚未通过审核";
            else if (selected.reviewed().isBefore(rule.startAt()) || rule.endAt()!=null && !selected.reviewed().isBefore(rule.endAt()))
                reason="录制通过时间不在活动期间";
        } else {
            String available="""
                SELECT s.id FROM practice_submission s WHERE s.user_id=? AND s.status='approved' AND s.updated_at>=?
                AND NOT EXISTS (SELECT 1 FROM recording_reward_claim c WHERE c.submission_id=s.id)
                """;
            var params=new ArrayList<Object>(List.of(actor,Timestamp.from(rule.startAt())));
            if (rule.endAt()!=null) {available+=" AND s.updated_at<?";params.add(Timestamp.from(rule.endAt()));}
            if (jdbc.queryForList(available+" LIMIT 1",String.class,params.toArray()).isEmpty()) reason="尚无活动期间通过且可领奖的录制";
        }
        return new Eligibility(rule,reason==null,0,reason,null);
    }
    private static final String AVAILABLE=RecordingRules.VISIBLE+" AND r.starts_at<=? AND (r.ends_at IS NULL OR r.ends_at>?)";
    @GetMapping("/rewards")
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    ApiResponse<Page<Eligibility>> rewards(@AuthenticationPrincipal UserPrincipal user,@RequestParam(required=false) @Size(max=36) String submissionId,
            @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        Evidence selected=submissionId==null?null:evidence(user.id(),submissionId);
        if (!rules.enabled()) return ApiResponse.ok(new Page<>(List.of(),page,pageSize,0));
        var now=Timestamp.from(clock.instant());
        return ApiResponse.ok(page(jdbc,"FROM recording_reward_rule r WHERE "+AVAILABLE,List.of(now,now),
            (r,n)->eligibility(user.id(),rules.rule(r,n),selected),page,pageSize));
    }
    @PostMapping("/reward-claims")
    ApiResponse<JsonNode> issue(@AuthenticationPrincipal UserPrincipal user,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Request body) {
        String actor=user.id();
        return ApiResponse.ok(snapshots.user(actor,"POST /reward-claims",key,body,()->{
            Evidence selected=evidence(actor,body.submissionId());
            var previous=jdbc.query("SELECT * FROM recording_reward_claim WHERE user_id=? AND submission_id=? AND rule_id=?",this::claimRow,actor,selected.id(),body.ruleId());
            if (!previous.isEmpty()) return json.valueToTree(previous.getFirst());
            if (!rules.enabled()) throw conflict("REWARDS_DISABLED","录制奖励尚未启用");
            // Serialize stock and rule changes across different recipients before evaluating the current policy.
            if (jdbc.queryForList("SELECT id FROM recording_reward_rule WHERE id=? FOR UPDATE",String.class,body.ruleId()).isEmpty())
                throw BusinessException.notFound();
            var now=Timestamp.from(clock.instant());
            var rule=jdbc.query("SELECT r.* FROM recording_reward_rule r WHERE r.id=? AND "+AVAILABLE,rules::rule,body.ruleId(),now,now)
                .stream().findFirst().orElseThrow(()->conflict("REWARD_UNAVAILABLE","该奖励当前不可领取"));
            Eligibility check=eligibility(actor,rule,selected);
            if (!check.eligible()) throw conflict("REWARD_INELIGIBLE",check.reason());
            String id=UUID.randomUUID().toString(),grant=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO entitlement_grant(id,user_id,kind,target_id,course_id,source,source_id,starts_at) VALUES (?,?,'course',?,?,'reward',?,?)",
                grant,actor,rule.courseId(),rule.courseId(),id,now);
            jdbc.update("""
                INSERT INTO recording_reward_claim(id,user_id,rule_id,rule_version,submission_id,session_id,course_id,entitlement_id,created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """,id,actor,rule.id(),rule.version(),selected.id(),selected.sessionId(),rule.courseId(),grant,now);
            if (jdbc.update("UPDATE recording_reward_rule SET issued=issued+1 WHERE id=? AND (stock IS NULL OR issued<stock)",rule.id())!=1)
                throw conflict("REWARD_UNAVAILABLE","库存不足");
            return json.valueToTree(lookup(actor,id));
        }));
    }
    private Claim lookup(String actor,String id) {
        return jdbc.query("SELECT * FROM recording_reward_claim WHERE user_id=? AND id=?",this::claimRow,actor,id)
            .stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    @GetMapping("/reward-claims/{id}")
    ApiResponse<Claim> one(@AuthenticationPrincipal UserPrincipal user,@PathVariable String id) {return ApiResponse.ok(lookup(user.id(),id));}
    @GetMapping("/me/reward-claims")
    ApiResponse<Page<Claim>> mine(@AuthenticationPrincipal UserPrincipal user,@RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(page(jdbc,"FROM recording_reward_claim r WHERE r.user_id=?",List.of(user.id()),this::claimRow,page,pageSize));
    }
    @GetMapping("/admin/reward-claims")
    @PreAuthorize("hasAuthority('rewards:manage')")
    ApiResponse<Page<Claim>> admin(@AuthenticationPrincipal AdminPrincipal admin,@RequestParam(required=false) @Size(max=36) String userId,
            @RequestParam(required=false) @Pattern(regexp="issued|processing|pending_address|shipped|completed|rejected") String status,
            @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        global(admin);
        var args=new ArrayList<Object>();
        String from="FROM recording_reward_claim r WHERE 1=1";
        if (userId!=null) {from+=" AND r.user_id=?";args.add(userId);}
        if (status!=null && !"issued".equals(status)) from+=" AND 1=0";
        return ApiResponse.ok(page(jdbc,from,args,this::claimRow,page,pageSize));
    }
}
