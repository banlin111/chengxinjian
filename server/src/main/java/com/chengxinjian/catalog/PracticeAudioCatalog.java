package com.chengxinjian.catalog;

import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.common.BusinessException;
import com.chengxinjian.user.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PracticeAudioCatalog {
    static final String FROM = """
        FROM practice_audio c JOIN media_asset a ON a.id=c.asset_id
        JOIN media_asset cover ON cover.id=c.cover_asset_id JOIN content_category k ON k.id=c.category_id
        LEFT JOIN content_teacher t ON t.id=c.teacher_id
        """;
    static final String VISIBLE = """
        c.status='published' AND k.status='published' AND k.kind=c.kind AND
        (c.teacher_id IS NULL OR t.status='published') AND cover.status='ready' AND cover.purpose='cover'
        AND cover.owner_admin_id IS NOT NULL AND a.owner_admin_id IS NOT NULL AND cover.review_status='approved' AND cover.public_url IS NOT NULL AND a.status='ready'
        AND a.review_status='approved' AND a.purpose=CONCAT(c.kind,'_media') AND a.mime_type LIKE 'audio/%'
        AND a.duration_seconds>0
        """;
    private static final String SELECT = "SELECT c.*,a.duration_seconds,cover.public_url ";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Entitlements entitlements;
    PracticeAudioCatalog(JdbcTemplate jdbc,ObjectMapper json,Entitlements entitlements) {
        this.jdbc=jdbc; this.json=json; this.entitlements=entitlements;
    }

    private CourseModels.Access access(UserPrincipal user,String policy) {
        if ("free".equals(policy)) return new CourseModels.Access(true,"free",0,null);
        if (user!=null) {
            var membership=entitlements.membership(user.id());
            if (membership.active()) return new CourseModels.Access(true,"member",0,membership.expiresAt());
        }
        return new CourseModels.Access(false,"locked",0,null);
    }

    public record Source(String kind,String asset,String key,int seconds,CourseModels.Access access) {}
    public Source authorize(UserPrincipal user,String kind,String id) {
        var source=jdbc.query("SELECT c.kind,c.asset_id,a.object_key,a.duration_seconds,c.access_policy "+FROM+
                " WHERE c.id=? AND c.kind=? AND "+VISIBLE,(r,n)->new Source(r.getString(1),r.getString(2),
                    r.getString(3),r.getInt(4),access(user,r.getString(5))),id,kind)
            .stream().findFirst().orElseThrow(BusinessException::notFound);
        if (!source.access().allowed()) throw new BusinessException(HttpStatus.FORBIDDEN,"CONTENT_LOCKED","此音频需要有效会员权益");
        return source;
    }

    private ObjectNode row(ResultSet r,UserPrincipal user,AdminPrincipal admin) throws SQLException {
        String id=r.getString("id"),teacher=r.getString("teacher_id"),policy=r.getString("access_policy");
        if (admin!=null) CourseCatalog.scope(admin,teacher);
        var out=json.createObjectNode().put("id",id).put("title",r.getString("title")).put("categoryId",r.getString("category_id"))
            .put("coverUrl",Objects.toString(r.getString("public_url"),"")).put("durationSeconds",r.getInt("duration_seconds"))
            .put("status",r.getString("status")).put("version",r.getInt("version")).put("accessPolicy",policy)
            .put("createdAt",r.getTimestamp("created_at").toInstant().toString()).put("updatedAt",r.getTimestamp("updated_at").toInstant().toString());
        out.set("access",json.valueToTree(access(user,policy)));
        if ("meditation".equals(r.getString("kind"))) {
            boolean favorite=user!=null && jdbc.queryForObject("SELECT COUNT(*) FROM user_content_relation WHERE user_id=? AND kind='meditation' AND target_id=? AND favorite=TRUE",Integer.class,user.id(),id)>0;
            out.put("summary",r.getString("summary")).put("teacherId",teacher).put("favorite",favorite);
        }
        if (admin!=null) out.put("assetId",r.getString("asset_id")).put("coverAssetId",r.getString("cover_asset_id"));
        return out;
    }

    ObjectNode detail(String kind,String id,UserPrincipal user,AdminPrincipal admin) {
        return jdbc.query(SELECT+FROM+" WHERE c.id=? AND c.kind=?"+(admin==null ? " AND "+VISIBLE : ""),
            (r,n)->row(r,user,admin),id,kind).stream().findFirst().orElseThrow(BusinessException::notFound);
    }

    CategoryModels.Page<ObjectNode> list(String kind,UserPrincipal user,AdminPrincipal admin,CourseModels.Filters filters,
                                         CourseModels.AdminFilters management,int page,int size) {
        var args=new ArrayList<Object>();
        var clauses=new ArrayList<String>();
        clauses.add("c.kind=?");args.add(kind);
        String order="c.created_at DESC,c.id";
        if (admin==null) clauses.add(VISIBLE);
        else if (!"all".equals(admin.dataScope())) {
            if (!"meditation".equals(kind) || admin.teacherId()==null) throw BusinessException.forbidden();
            filter(clauses,args,"c.teacher_id=?",admin.teacherId());
        }
        if (filters!=null) {
            if (filters.difficulty()!=null) throw BusinessException.invalid("冥想没有难度筛选字段");
            if (filters.minSeconds()!=null && filters.maxSeconds()!=null && filters.minSeconds()>filters.maxSeconds())
                throw BusinessException.invalid("时长范围无效");
            filter(clauses,args,"c.category_id=?",filters.categoryId());filter(clauses,args,"c.teacher_id=?",filters.teacherId());
            filter(clauses,args,"a.duration_seconds>=?",filters.minSeconds());filter(clauses,args,"a.duration_seconds<=?",filters.maxSeconds());
            if (filters.memberOnly()!=null) filter(clauses,args,"c.access_policy=?",filters.memberOnly()?"member":"free");
            if ("duration_asc".equals(filters.sort())) order="a.duration_seconds,c.id";
        }
        if (management!=null) {
            if (management.courseId()!=null || ("noise".equals(kind) && management.teacherId()!=null))
                throw BusinessException.invalid("不支持该内容筛选");
            filter(clauses,args,"c.category_id=?",management.categoryId());filter(clauses,args,"c.teacher_id=?",management.teacherId());
            filter(clauses,args,"c.status=?",management.status());
            if (management.q()!=null) filter(clauses,args,"c.title LIKE ? ESCAPE '!'","%"+management.q().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        }
        String from=FROM+" WHERE "+String.join(" AND ",clauses);
        long total=jdbc.queryForObject("SELECT COUNT(*) "+from,Long.class,args.toArray());
        args.add(size);args.add(((long)page-1)*size);
        return new CategoryModels.Page<>(jdbc.query(SELECT+from+" ORDER BY "+order+" LIMIT ? OFFSET ?",(r,n)->row(r,user,admin),args.toArray()),page,size,total);
    }
    private static void filter(List<String> clauses,List<Object> args,String clause,Object value) {
        if (value!=null) {clauses.add(clause);args.add(value);}
    }
}
