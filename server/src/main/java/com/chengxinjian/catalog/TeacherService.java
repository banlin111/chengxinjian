package com.chengxinjian.catalog;

import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.auth.TokenSecrets;
import com.chengxinjian.common.AuditService;
import com.chengxinjian.common.BusinessException;
import com.chengxinjian.user.UserPrincipal;
import com.chengxinjian.user.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.chengxinjian.catalog.TeacherModels.*;
import static com.chengxinjian.catalog.CategoryModels.Page;

@Service
public class TeacherService {
    private static final String SELECT="""
        SELECT t.*,a.public_url AS avatar_url FROM content_teacher t
        LEFT JOIN media_asset a ON a.id=t.avatar_asset_id AND a.status='ready' AND a.purpose='avatar' AND a.review_status='approved'
        """;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final AuditService audit;
    private final UserService users;
    private final RowMapper<AdminTeacher> mapper;
    public TeacherService(JdbcTemplate jdbc,ObjectMapper json,Clock clock,AuditService audit,UserService users) {
        this.jdbc=jdbc;this.json=json;this.clock=clock;this.audit=audit;this.users=users;
        mapper=(r,n)->new AdminTeacher(r.getString("id"),r.getString("name"),r.getString("avatar_url"),r.getString("bio"),
            credentials(r.getString("credentials_json")),false,r.getString("status"),r.getInt("version"),
            r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),r.getString("avatar_asset_id"),r.getString("user_id"));
    }
    public Page<Teacher> published(UserPrincipal user,int page,int pageSize,String q) {
        var args=new ArrayList<Object>();String where=" WHERE t.status='published'";
        if (q!=null && !q.isBlank()) { where+=" AND t.name LIKE ? ESCAPE '!'";args.add(search(q)); }
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM content_teacher t"+where,Long.class,args.toArray());
        args.add(pageSize);args.add(((long)page-1)*pageSize);
        var rows=jdbc.query(SELECT+where+" ORDER BY t.created_at,t.id LIMIT ? OFFSET ?",mapper,args.toArray());
        Set<String> followed=user==null ? Set.of() : new HashSet<>(jdbc.queryForList("SELECT teacher_id FROM teacher_follow WHERE user_id=? AND followed=TRUE",String.class,user.id()));
        return new Page<>(rows.stream().map(t->t.publicView(followed.contains(t.id()))).toList(),page,pageSize,total);
    }
    public Teacher publishedGet(UserPrincipal user,String id) {
        var teacher=find(id,false);
        if (!"published".equals(teacher.status())) { throw BusinessException.notFound(); }
        return teacher.publicView(user!=null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM teacher_follow WHERE user_id=? AND teacher_id=? AND followed=TRUE",Boolean.class,user.id(),id)));
    }
    public Page<AdminTeacher> list(AdminPrincipal admin,int page,int pageSize,String q,String status,String teacherId) {
        var args=new ArrayList<Object>();String where=" WHERE 1=1";
        if (!"all".equals(admin.dataScope())) {
            if (!"own_content".equals(admin.dataScope()) || admin.teacherId()==null) { throw BusinessException.forbidden(); }
            if (teacherId!=null && !teacherId.equals(admin.teacherId())) { throw BusinessException.forbidden(); }
            where+=" AND t.id=?";args.add(admin.teacherId());
        } else if (teacherId!=null) { where+=" AND t.id=?";args.add(teacherId); }
        if (q!=null && !q.isBlank()) { where+=" AND t.name LIKE ? ESCAPE '!'";args.add(search(q)); }
        if (status!=null) { where+=" AND t.status=?";args.add(status); }
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM content_teacher t"+where,Long.class,args.toArray());
        args.add(pageSize);args.add(((long)page-1)*pageSize);
        return new Page<>(jdbc.query(SELECT+where+" ORDER BY t.created_at,t.id LIMIT ? OFFSET ?",mapper,args.toArray()),page,pageSize,total);
    }
    public AdminTeacher get(AdminPrincipal admin,String id) { requireScope(admin,id);return find(id,false); }
    private AdminTeacher find(String id,boolean lock) {
        // Lock only the teacher row. A joined FOR UPDATE can also lock media and invert lock order.
        if (lock) {
            var ids=jdbc.queryForList("SELECT id FROM content_teacher WHERE id=? FOR UPDATE",String.class,id);
            if (ids.isEmpty()) { throw BusinessException.notFound(); }
        }
        return jdbc.query(SELECT+" WHERE t.id=?",mapper,id).stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public AdminTeacher create(AdminPrincipal admin,String key,Input input) {
        if (!"all".equals(admin.dataScope())) { throw BusinessException.forbidden(); }
        if (key.length()!=36) { throw BusinessException.invalid("Idempotency-Key 必须为 UUID"); }
        try { UUID.fromString(key); } catch (IllegalArgumentException e) { throw BusinessException.invalid("Idempotency-Key 必须为 UUID"); }
        String operation="POST /admin/content/teachers";String hash=TokenSecrets.hash(encode(input));
        jdbc.queryForObject("SELECT id FROM admin_account WHERE id=? FOR UPDATE",String.class,admin.id());
        var records=jdbc.queryForList("SELECT request_hash,result_json FROM idempotency_record WHERE actor_id=? AND operation=? AND idempotency_key=?",admin.id(),operation,key);
        if (!records.isEmpty()) {
            if (!hash.equals(records.getFirst().get("request_hash"))) { throw conflict("IDEMPOTENCY_CONFLICT","相同幂等标识不能用于不同请求"); }
            return decode((String)records.getFirst().get("result_json"),AdminTeacher.class);
        }
        validateReferences(admin,input,null);
        String id=UUID.randomUUID().toString();var now=Timestamp.from(clock.instant());
        try { jdbc.update("INSERT INTO content_teacher(id,name,bio,credentials_json,avatar_asset_id,user_id,status,version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,1,?,?)",
            id,input.name(),input.bio(),encode(input.credentials()),input.avatarAssetId(),input.userId(),input.status(),now,now); }
        catch (DuplicateKeyException e) { throw conflict("TEACHER_USER_ALREADY_BOUND","该用户已关联其他讲师，请先核实原关联"); }
        var result=find(id,false);
        jdbc.update("INSERT INTO idempotency_record(actor_id,operation,idempotency_key,request_hash,result_id,result_json,created_at) VALUES (?,?,?,?,?,?,?)",
            admin.id(),operation,key,hash,id,encode(result),now);
        audit.record(admin.id(),"content.teacher.create",id,null);return result;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public AdminTeacher update(AdminPrincipal admin,String id,Update update) {
        requireScope(admin,id);var current=find(id,true);
        if (current.version()!=update.version()) { throw conflict("VERSION_CONFLICT","讲师资料已修改，请重新读取"); }
        if (!"all".equals(admin.dataScope()) && !Objects.equals(current.userId(),update.userId())) { throw BusinessException.forbidden(); }
        checkCourses(id,update.status());
        validateReferences(admin,update.input(),id);
        try { jdbc.update("UPDATE content_teacher SET name=?,bio=?,credentials_json=?,avatar_asset_id=?,user_id=?,status=?,version=version+1,updated_at=? WHERE id=? AND version=?",
            update.name(),update.bio(),encode(update.credentials()),update.avatarAssetId(),update.userId(),update.status(),Timestamp.from(clock.instant()),id,update.version()); }
        catch (DuplicateKeyException e) { throw conflict("TEACHER_USER_ALREADY_BOUND","该用户已关联其他讲师，请先核实原关联"); }
        audit.record(admin.id(),"content.teacher.update",id,null);return find(id,false);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void archive(AdminPrincipal admin,String id,CategoryModels.Archive input) {
        requireScope(admin,id);var current=find(id,true);
        if ("archived".equals(current.status())) { return; }
        if (current.version()!=input.version()) { throw conflict("VERSION_CONFLICT","讲师资料已修改，请重新读取"); }
        checkCourses(id,"archived");
        jdbc.update("UPDATE content_teacher SET status='archived',version=version+1,updated_at=? WHERE id=?",Timestamp.from(clock.instant()),id);
        audit.record(admin.id(),"content.teacher.archive",id,input.reason());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void follow(UserPrincipal user,String id,boolean followed) {
        users.requireActive(user.id(),false);var teacher=find(id,true);
        if (!"published".equals(teacher.status())) { throw BusinessException.notFound(); }
        jdbc.update("""
            INSERT INTO teacher_follow(user_id,teacher_id,followed,updated_at) VALUES (?,?,?,?)
            ON DUPLICATE KEY UPDATE followed=?,updated_at=?
            """,user.id(),id,followed,Timestamp.from(clock.instant()),followed,Timestamp.from(clock.instant()));
    }
    private void validateReferences(AdminPrincipal admin,Input input,String id) {
        if (input.userId()!=null) {
            if (input.userId().isBlank()) { throw BusinessException.invalid("关联用户不能为空字符串"); }
            var rows=jdbc.queryForList("SELECT id,status FROM app_user WHERE id=? FOR UPDATE",input.userId());
            if (rows.isEmpty() || !"active".equals(rows.getFirst().get("status"))) { throw BusinessException.invalid("关联用户不存在或不可用"); }
            var bound=jdbc.queryForList("SELECT id FROM content_teacher WHERE user_id=?",String.class,input.userId());
            if (!bound.isEmpty() && !bound.getFirst().equals(id)) { throw conflict("TEACHER_USER_ALREADY_BOUND","该用户已关联其他讲师，请先核实原关联"); }
        }
        if (input.avatarAssetId()!=null) {
            var rows=jdbc.queryForList("SELECT * FROM media_asset WHERE id=?",input.avatarAssetId());
            if (rows.isEmpty()) { throw BusinessException.invalid("头像资产不存在"); }
            var asset=rows.getFirst();
            if (!"avatar".equals(asset.get("purpose")) || !"ready".equals(asset.get("status")) || !"approved".equals(asset.get("review_status")) || asset.get("public_url")==null) {
                throw conflict("ASSET_NOT_READY","头像须为已处理完成的头像图片");
            }
            if (!List.of("image/jpeg","image/png","image/webp").contains(asset.get("mime_type"))) { throw BusinessException.invalid("头像图片类型不支持"); }
            if (asset.get("owner_user_id")!=null && !asset.get("owner_user_id").equals(input.userId())) { throw BusinessException.forbidden(); }
            boolean assigned=id!=null && jdbc.queryForObject("SELECT COUNT(*) FROM content_teacher WHERE id=? AND avatar_asset_id=?",Long.class,id,input.avatarAssetId())>0;
            if (asset.get("owner_admin_id")!=null && !"all".equals(admin.dataScope()) && !admin.id().equals(asset.get("owner_admin_id")) && !assigned) {
                throw BusinessException.forbidden();
            }
            if (((Number)asset.get("size_bytes")).longValue()>10*1024*1024) { throw BusinessException.invalid("头像图片超过大小限制"); }
            try {
                var url=java.net.URI.create((String)asset.get("public_url"));
                boolean reviewedLocal="approved".equals(asset.get("review_status")) && "http".equals(url.getScheme()) && "127.0.0.1".equals(url.getHost()) && ("/media/images/"+input.avatarAssetId()).equals(url.getPath());
                if ((!reviewedLocal && !"https".equals(url.getScheme())) || url.getHost()==null || url.getUserInfo()!=null) { throw BusinessException.invalid("头像地址不可用"); }
            } catch (IllegalArgumentException e) { throw BusinessException.invalid("头像地址不可用"); }
        }
    }
    private void checkCourses(String id,String status) {
        if ("published".equals(status)) return;
        String active="archived".equals(status) ? " AND status<>'archived'" : " AND status='published'";
        if(jdbc.queryForObject("SELECT COUNT(*) FROM content_course WHERE teacher_id=?"+active,Long.class,id)>0)
            throw conflict("CONTENT_IN_USE","请先调整此讲师的课程");
    }
    private void requireScope(AdminPrincipal admin,String id) {
        if (admin==null || !("all".equals(admin.dataScope()) || ("own_content".equals(admin.dataScope()) && id.equals(admin.teacherId())))) { throw BusinessException.forbidden(); }
    }
    private String search(String q) { return "%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%"; }
    private List<String> credentials(String text) {
        try { return json.readValue(text,new TypeReference<List<String>>(){}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored credentials"); }
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize teacher"); }
    }
    private <T> T decode(String text,Class<T> type) {
        try { return json.readValue(text,type); } catch (JsonProcessingException e) { throw new IllegalStateException("Cannot read teacher snapshot"); }
    }
    private BusinessException conflict(String code,String message) { return new BusinessException(HttpStatus.CONFLICT,code,message); }
}
