package com.chengxinjian.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.chengxinjian.catalog.CategoryModels.Page;

@Repository
class LearningHistory {
    private static final String VISIBLE_PROGRESS = CourseCatalog.LESSON_FROM+" JOIN lesson_progress p ON p.lesson_id=l.id AND p.asset_id=l.asset_id WHERE p.user_id=? AND "+CourseCatalog.COURSE_VISIBLE+" AND "+CourseCatalog.LESSON_VISIBLE;
    private static final String SELECT_ITEM = "SELECT l.course_id,l.title,cover.public_url,a.duration_seconds,p.*";
    private static final String RECENT_FIRST = " ORDER BY p.last_played_at DESC,l.id";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    LearningHistory(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    ObjectNode latestUnfinished(String userId) {
        return jdbc.query(SELECT_ITEM + VISIBLE_PROGRESS + " AND p.completed=FALSE" + RECENT_FIRST + " LIMIT 1",
            this::item, userId).stream().findFirst().orElse(null);
    }

    Page<ObjectNode> page(String userId, String state, int page, int pageSize) {
        String from = VISIBLE_PROGRESS;
        List<Object> args=new ArrayList<>(List.of(userId));
        if("completed".equals(state)) from+=" AND p.completed=TRUE";
        if("in_progress".equals(state)) from+=" AND p.completed=FALSE";
        if("purchased".equals(state)) {
            // This filter means active direct-course entitlement, including manual grants; never evidence of payment.
            from+=" AND EXISTS (SELECT 1 FROM entitlement_grant g WHERE g.user_id=p.user_id AND g.kind='course' AND g.course_id=c.id AND g.revoked_at IS NULL AND g.starts_at<=? AND (g.expires_at IS NULL OR g.expires_at>?))";
            var now=Timestamp.from(clock.instant());args.add(now);args.add(now);
        }
        long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());args.add(pageSize);args.add(((long)page-1)*pageSize);
        var items = jdbc.query(SELECT_ITEM + from + RECENT_FIRST + " LIMIT ? OFFSET ?", this::item, args.toArray());
        return new Page<>(items, page, pageSize, total);
    }

    private ObjectNode item(ResultSet r, int index) throws SQLException {
        int duration=r.getInt("duration_seconds");
        var result=json.createObjectNode().put("title",r.getString("title")).put("coverUrl",r.getString("public_url"))
            .put("courseId",r.getString("course_id")).put("lessonId",r.getString("lesson_id")).put("positionSeconds",r.getInt("position_seconds"))
            .put("progressPercent",duration<=0?0:Math.min(100,100L*r.getInt("credited_seconds")/duration)).put("completed",r.getBoolean("completed"))
            .put("lastPlayedAt",r.getTimestamp("last_played_at").toInstant().toString());
        result.putObject("target").put("kind","lesson").put("id",r.getString("lesson_id"));return result;
    }
}
