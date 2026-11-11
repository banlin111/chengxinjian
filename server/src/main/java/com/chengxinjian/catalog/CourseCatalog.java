package com.chengxinjian.catalog;

import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.common.BusinessException;
import com.chengxinjian.user.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import static com.chengxinjian.catalog.CourseModels.*;

@Service
public class CourseCatalog {
    static final String COURSE_FROM="""
        FROM content_course c JOIN content_teacher t ON t.id=c.teacher_id
        JOIN content_category k ON k.id=c.category_id JOIN media_asset cover ON cover.id=c.cover_asset_id
        """;
    static final String COURSE_VISIBLE="""
        c.status='published' AND t.status='published' AND k.kind='course' AND k.status='published'
        AND cover.status='ready' AND cover.review_status='approved' AND cover.public_url IS NOT NULL
        """;
    static final String LESSON_FROM=" FROM content_lesson l JOIN media_asset a ON a.id=l.asset_id JOIN content_course c ON c.id=l.course_id JOIN content_teacher t ON t.id=c.teacher_id JOIN content_category k ON k.id=c.category_id JOIN media_asset cover ON cover.id=c.cover_asset_id ";
    static final String LESSON_VISIBLE="l.status='published' AND a.status='ready' AND a.review_status='approved' AND a.purpose='course_media'";
    private static final String TOTALS="""
        LEFT JOIN (SELECT l.course_id,COUNT(*) AS lessons,SUM(a.duration_seconds) AS duration,
               MAX(CASE WHEN tr.state='ready' THEN l.trial_seconds ELSE 0 END) AS trial_seconds
        FROM content_lesson l JOIN media_asset a ON a.id=l.asset_id
        LEFT JOIN lesson_trial tr ON tr.asset_id=l.asset_id AND tr.seconds=l.trial_seconds WHERE
        """+LESSON_VISIBLE+" GROUP BY l.course_id) totals ON totals.course_id=c.id ";
    private static final String COURSE_SELECT="SELECT c.*,cover.public_url,COALESCE(totals.lessons,0) AS lesson_count,COALESCE(totals.duration,0) AS duration,COALESCE(totals.trial_seconds,0) AS available_trial ";
    private static final String LESSON_SELECT="""
        SELECT l.*,a.duration_seconds,a.mime_type,c.access_policy,c.teacher_id,
        CASE WHEN EXISTS (SELECT 1 FROM lesson_trial tr WHERE tr.asset_id=l.asset_id AND tr.seconds=l.trial_seconds AND tr.state='ready')
          THEN l.trial_seconds ELSE 0 END AS available_trial
        """;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Entitlements entitlements;
    public CourseCatalog(JdbcTemplate jdbc,ObjectMapper json,Entitlements entitlements) {
        this.jdbc=jdbc; this.json=json; this.entitlements=entitlements;
    }

    static void scope(AdminPrincipal admin,String teacher) {
        if (!"all".equals(admin.dataScope()) && !("own_content".equals(admin.dataScope()) && teacher!=null && teacher.equals(admin.teacherId())))
            throw BusinessException.forbidden();
    }
    private static class Where {
        StringBuilder sql=new StringBuilder(" WHERE 1=1 ");
        List<Object> args=new ArrayList<>();
        void test(String field,Object value) {
            if (value!=null) { sql.append(" AND ").append(field); args.add(value); }
        }
    }
    private <T> CategoryModels.Page<T> page(String select,String from,Where where,String order,RowMapper<T> mapper,int page,int size) {
        long total=jdbc.queryForObject("SELECT COUNT(*) "+from+where.sql,Long.class,where.args.toArray());
        where.args.add(size); where.args.add(((long)page-1)*size);
        var items=jdbc.query(select+from+where.sql+" ORDER BY "+order+" LIMIT ? OFFSET ?",mapper,where.args.toArray());
        return new CategoryModels.Page<>(items,page,size,total);
    }
    public CategoryModels.Page<ObjectNode> courses(UserPrincipal user,Filters f,int page,int size) {
        var where=new Where(); where.sql.append(" AND ").append(COURSE_VISIBLE);
        where.test("c.category_id=?",f.categoryId()); where.test("c.teacher_id=?",f.teacherId()); where.test("c.difficulty=?",f.difficulty());
        if (f.minSeconds()!=null && f.maxSeconds()!=null && f.minSeconds()>f.maxSeconds()) throw BusinessException.invalid("时长范围错误");
        where.test("COALESCE(totals.duration,0)>=?",f.minSeconds()); where.test("COALESCE(totals.duration,0)<=?",f.maxSeconds());
        if (f.memberOnly()!=null) where.sql.append(f.memberOnly() ? " AND c.access_policy='member'" : " AND c.access_policy<>'member'");
        String order="duration_asc".equals(f.sort()) ? "COALESCE(totals.duration,0),c.id" : "c.created_at DESC,c.id";
        return page(COURSE_SELECT,COURSE_FROM+TOTALS,where,order,(r,n)->course(r,user,false),page,size);
    }
    public ObjectNode course(String id,AdminPrincipal admin,UserPrincipal user) {
        return jdbc.query(COURSE_SELECT+COURSE_FROM+TOTALS+" WHERE c.id=?"+(admin==null ? " AND "+COURSE_VISIBLE : ""),
            (r,n)->{if(admin!=null) scope(admin,r.getString("teacher_id")); return course(r,user,admin!=null);},id)
            .stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    public CategoryModels.Page<ObjectNode> lessons(String courseId,UserPrincipal user,int page,int size) {
        course(courseId,null,user);
        var where=new Where(); where.test("c.id=?",courseId); where.sql.append(" AND ").append(COURSE_VISIBLE).append(" AND ").append(LESSON_VISIBLE);
        return page(LESSON_SELECT,LESSON_FROM,where,"l.sort_order,l.id",(r,n)->lesson(r,user,false),page,size);
    }
    public ObjectNode lesson(String id,AdminPrincipal admin,UserPrincipal user) {
        String visible=admin==null ? " AND "+COURSE_VISIBLE+" AND "+LESSON_VISIBLE : "";
        return jdbc.query(LESSON_SELECT+LESSON_FROM+" WHERE l.id=?"+visible,(r,n)->{
            if (admin!=null) scope(admin,r.getString("teacher_id"));
            return lesson(r,user,admin!=null);
        },id).stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    public CategoryModels.Page<ObjectNode> adminList(AdminPrincipal admin,boolean lessons,AdminFilters f,int page,int size) {
        var where=new Where();
        if (!"all".equals(admin.dataScope())) {
            scope(admin,f.teacherId()!=null ? f.teacherId() : admin.teacherId());
            where.test("c.teacher_id=?",admin.teacherId());
        } else where.test("c.teacher_id=?",f.teacherId());
        where.test("c.category_id=?",f.categoryId()); where.test("c.id=?",f.courseId());
        String alias=lessons ? "l." : "c.";
        where.test(alias+"status=?",f.status());
        if (f.q()!=null) where.test(alias+"title LIKE ? ESCAPE '!'", "%"+f.q().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        if (lessons) return page(LESSON_SELECT,LESSON_FROM,where,"c.id,l.sort_order,l.id",(r,n)->lesson(r,null,true),page,size);
        return page(COURSE_SELECT,COURSE_FROM+TOTALS,where,"c.created_at DESC,c.id",(r,n)->course(r,null,true),page,size);
    }
    private ObjectNode base(ResultSet r) throws SQLException {
        return json.createObjectNode().put("id",r.getString("id")).put("title",r.getString("title")).put("status",r.getString("status"))
            .put("version",r.getInt("version")).put("createdAt",r.getTimestamp("created_at").toInstant().toString())
            .put("updatedAt",r.getTimestamp("updated_at").toInstant().toString());
    }
    private ObjectNode course(ResultSet r,UserPrincipal user,boolean admin) throws SQLException {
        String id=r.getString("id"),policy=r.getString("access_policy");
        var result=base(r).put("summary",r.getString("summary")).put("description",r.getString("description"))
            .put("coverUrl",Objects.toString(r.getString("public_url"),"")).put("categoryId",r.getString("category_id"))
            .put("teacherId",r.getString("teacher_id")).put("difficulty",r.getString("difficulty")).put("totalSeconds",r.getLong("duration"))
            .put("lessonCount",r.getLong("lesson_count")).put("priceFen",r.getInt("price_fen")).put("originalPriceFen",(Integer)r.getObject("original_price_fen"))
            .put("accessPolicy",policy).put("favorite",false).put("liked",false);
        result.put("likeCount",jdbc.queryForObject("SELECT COUNT(*) FROM user_content_relation WHERE kind='course' AND target_id=? AND liked=TRUE",Long.class,id));
        if(user!=null) jdbc.query("SELECT favorite,liked FROM user_content_relation WHERE user_id=? AND kind='course' AND target_id=?",
            (org.springframework.jdbc.core.RowCallbackHandler)row->{result.put("favorite",row.getBoolean(1));result.put("liked",row.getBoolean(2));},user.id(),id);
        result.put("learnerCount",jdbc.queryForObject("SELECT COUNT(DISTINCT p.user_id) FROM lesson_progress p JOIN content_lesson l ON l.id=p.lesson_id WHERE l.course_id=? AND p.credited_seconds>0",Long.class,id));
        long duration=r.getLong("duration");
        long credited=user==null || duration==0 ? 0 : jdbc.queryForObject("SELECT COALESCE(SUM(p.credited_seconds),0) FROM lesson_progress p JOIN content_lesson l ON l.id=p.lesson_id AND l.asset_id=p.asset_id JOIN media_asset a ON a.id=l.asset_id WHERE "+LESSON_VISIBLE+" AND l.course_id=? AND p.user_id=?",Long.class,id,user.id());
        result.put("progressPercent",duration==0 ? 0 : Math.min(100,100*credited/duration));
        result.set("access",json.valueToTree(entitlements.access(user,id,policy,r.getInt("available_trial"))));
        if (admin) result.put("coverAssetId",r.getString("cover_asset_id"));
        return result;
    }
    private ObjectNode lesson(ResultSet r,UserPrincipal user,boolean admin) throws SQLException {
        String course=r.getString("course_id"),id=r.getString("id");
        var result=base(r).put("courseId",course).put("sort",r.getInt("sort_order")).put("durationSeconds",r.getInt("duration_seconds"))
            .put("mediaKind",r.getString("mime_type").startsWith("audio/")?"audio":"video").put("trialSeconds",r.getInt("trial_seconds"))
            .put("resumeSeconds",0).put("completed",false);
        var access=entitlements.access(user,course,r.getString("access_policy"),r.getInt("available_trial"));
        result.set("access",json.valueToTree(access));
        if (user!=null) jdbc.query("SELECT position_seconds,completed FROM lesson_progress WHERE user_id=? AND lesson_id=? AND asset_id=?",
            (org.springframework.jdbc.core.RowCallbackHandler)row->{result.put("resumeSeconds",row.getInt(1)); result.put("completed",row.getBoolean(2));},user.id(),id,r.getString("asset_id"));
        if (admin) result.put("assetId",r.getString("asset_id"));
        return result;
    }
}
