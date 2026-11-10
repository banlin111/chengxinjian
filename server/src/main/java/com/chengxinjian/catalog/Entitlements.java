package com.chengxinjian.catalog;

import com.chengxinjian.user.UserModels.Membership;
import com.chengxinjian.user.UserPrincipal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import static com.chengxinjian.catalog.CourseModels.Access;

@Service
public class Entitlements {
    public record Grant(String id,String kind,String targetId,String source,Instant startsAt,Instant expiresAt,String status) {}
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public Entitlements(JdbcTemplate jdbc,Clock clock) { this.jdbc=jdbc; this.clock=clock; }
    private static final String ACTIVE="""
        SELECT target_id,starts_at,expires_at,(SELECT name FROM membership_plan WHERE id=target_id) AS plan_name FROM entitlement_grant
        WHERE user_id=? AND revoked_at IS NULL AND starts_at<=? AND (expires_at IS NULL OR expires_at>?)
        """;
    private static final String LATEST=" ORDER BY CASE WHEN expires_at IS NULL THEN 0 ELSE 1 END,expires_at DESC,starts_at,id LIMIT 1";
    public Membership membership(String user) {
        var now=Timestamp.from(clock.instant());
        return jdbc.query(ACTIVE+" AND kind='membership'"+LATEST,(r,n)->{
            Timestamp end=r.getTimestamp("expires_at");
            return new Membership(true,r.getString("target_id"),r.getString("plan_name"),r.getTimestamp("starts_at").toInstant(),end==null?null:end.toInstant(),end==null);
        },user,now,now).stream().findFirst().orElse(new Membership(false,null,null,null,null,false));
    }
    public Access access(UserPrincipal user,String course,String policy,int trial) {
        if ("free".equals(policy)) return new Access(true,"free",0,null);
        if (user!=null) {
            // Existing single-course grants survive changes in how the course is sold.
            var now=Timestamp.from(clock.instant());
            var owned=jdbc.query(ACTIVE+" AND kind='course' AND course_id=?"+LATEST,(r,n)->{
                Timestamp end=r.getTimestamp("expires_at");
                return new Access(true,"purchased",0,end==null?null:end.toInstant());
            },user.id(),now,now,course);
            if (!owned.isEmpty()) return owned.getFirst();
            if (!"purchase".equals(policy)) {
                var membership=membership(user.id());
                if (membership.active()) return new Access(true,"member",0,membership.expiresAt());
            }
        }
        return trial>0 ? new Access(true,"trial",trial,null) : new Access(false,"locked",0,null);
    }
    Grant row(java.sql.ResultSet r,Instant now) throws java.sql.SQLException {
        Timestamp end=r.getTimestamp("expires_at");
        String status=r.getTimestamp("revoked_at")!=null ? "revoked" : end!=null && !end.toInstant().isAfter(now) ? "expired" : "active";
        return new Grant(r.getString("id"),r.getString("kind"),r.getString("target_id"),r.getString("source"),
            r.getTimestamp("starts_at").toInstant(),end==null?null:end.toInstant(),status);
    }
    public CategoryModels.Page<Grant> page(String user,int page,int size) {
        var now=clock.instant();
        var rows=jdbc.query("SELECT * FROM entitlement_grant WHERE user_id=? ORDER BY starts_at DESC,id LIMIT ? OFFSET ?",(r,n)->{
            return row(r,now);
        },user,size,((long)page-1)*size);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM entitlement_grant WHERE user_id=?",Long.class,user);
        return new CategoryModels.Page<>(rows,page,size,total);
    }
}
