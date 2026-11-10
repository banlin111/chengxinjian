package com.chengxinjian.catalog;

import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.chengxinjian.catalog.CourseModels.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class CourseEditor {
    private final JdbcTemplate jdbc;
    private final CourseCatalog catalog;
    private final CreateSnapshot snapshots;
    private final AuditService audit;
    private final Clock clock;
    public CourseEditor(JdbcTemplate jdbc,CourseCatalog catalog,CreateSnapshot snapshots,AuditService audit,Clock clock) {
        this.jdbc=jdbc; this.catalog=catalog; this.snapshots=snapshots; this.audit=audit; this.clock=clock;
    }
    private Timestamp now() { return Timestamp.from(clock.instant()); }
    private static BusinessException conflict(String code,String message) { return new BusinessException(HttpStatus.CONFLICT,code,message); }
    private Map<String,Object> lock(String table,String id) {
        return jdbc.queryForList("SELECT * FROM "+table+" WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    private void version(Map<String,Object> row,int version) {
        if (((Number)row.get("version")).intValue()!=version) throw conflict("VERSION_CONFLICT","内容已修改，请刷新");
    }
    private Map<String,Object> asset(AdminPrincipal admin,String id,String purpose,boolean assigned) {
        var asset=lock("media_asset",id);
        if (asset.get("owner_admin_id")==null || (!"all".equals(admin.dataScope()) && !assigned && !admin.id().equals(asset.get("owner_admin_id"))))
            throw BusinessException.forbidden();
        if (!purpose.equals(asset.get("purpose")) || !"ready".equals(asset.get("status")))
            throw conflict("ASSET_NOT_READY","素材用途不匹配或尚未完成技术校验");
        return asset;
    }
    public JsonNode createCourse(AdminPrincipal admin,String key,CourseInput input) {
        CourseCatalog.scope(admin,input.teacherId());
        return snapshots.admin(admin.id(),"POST /admin/content/courses",key,input,id->catalog.course(id,admin,null),
            ()->saveCourse(admin,null,input,null));
    }
    public JsonNode saveCourse(AdminPrincipal admin,String id,CourseInput input,Integer expected) {
        CourseCatalog.scope(admin,input.teacherId());
        boolean create=id==null;
        var old=create ? Map.<String,Object>of() : lock("content_course",id);
        if (!create) {
            CourseCatalog.scope(admin,(String)old.get("teacher_id")); version(old,expected);
        }
        var category=lock("content_category",input.categoryId());
        var teacher=lock("content_teacher",input.teacherId());
        boolean published="published".equals(input.status());
        if (!"course".equals(category.get("kind")) || "archived".equals(category.get("status")) || "archived".equals(teacher.get("status"))
            || published && (!"published".equals(category.get("status")) || !"published".equals(teacher.get("status"))))
            throw conflict("CONTENT_REFERENCE_INVALID","分类或讲师的状态不允许引用");
        var cover=asset(admin,input.coverAssetId(),"cover",input.coverAssetId().equals(old.get("cover_asset_id")));
        if (!"approved".equals(cover.get("review_status")) || cover.get("public_url")==null)
            throw conflict("ASSET_NOT_READY","封面须通过审核并取得图片地址");
        boolean purchasable=Set.of("purchase","member_or_purchase").contains(input.accessPolicy());
        if ((purchasable && input.priceFen()==0) || ("free".equals(input.accessPolicy()) && input.priceFen()!=0) ||
            (input.originalPriceFen()!=null && input.originalPriceFen()<input.priceFen())) throw BusinessException.invalid("课程价格与访问策略不一致");
        if (create) {
            id=UUID.randomUUID().toString();
            jdbc.update("""
                INSERT INTO content_course(title,summary,description,cover_asset_id,category_id,teacher_id,difficulty,price_fen,original_price_fen,access_policy,status,id,version,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,1,?,?)
                """,input.title(),input.summary(),input.description(),input.coverAssetId(),input.categoryId(),input.teacherId(),
                input.difficulty(),input.priceFen(),input.originalPriceFen(),input.accessPolicy(),input.status(),id,now(),now());
        } else {
            jdbc.update("""
                UPDATE content_course SET title=?,summary=?,description=?,cover_asset_id=?,category_id=?,teacher_id=?,difficulty=?,
                    price_fen=?,original_price_fen=?,access_policy=?,status=?,version=version+1,updated_at=? WHERE id=?
                """,input.title(),input.summary(),input.description(),input.coverAssetId(),input.categoryId(),input.teacherId(),input.difficulty(),
                input.priceFen(),input.originalPriceFen(),input.accessPolicy(),input.status(),now(),id);
        }
        jdbc.update("INSERT INTO content_reference_history(course_id,asset_id,teacher_id,category_id,revision) VALUES (?,?,?,?,?)",
            id,input.coverAssetId(),input.teacherId(),input.categoryId(),create ? 1 : expected+1);
        audit.record(admin.id(),create ? "content.course.create" : "content.course.update",id,null);
        return catalog.course(id,admin,null);
    }
    public JsonNode createLesson(AdminPrincipal admin,String key,LessonInput input) {
        return snapshots.admin(admin.id(),"POST /admin/content/lessons",key,input,id->catalog.lesson(id,admin,null),
            ()->saveLesson(admin,null,input,null));
    }
    public JsonNode saveLesson(AdminPrincipal admin,String id,LessonInput input,Integer expected) {
        var course=lock("content_course",input.courseId());
        CourseCatalog.scope(admin,(String)course.get("teacher_id"));
        boolean create=id==null;
        var old=create ? Map.<String,Object>of() : lock("content_lesson",id);
        if (!create) {
            if (!input.courseId().equals(old.get("course_id"))) throw conflict("CONTENT_REFERENCE_INVALID","课节已有课程归属，请在目标课程新建");
            version(old,expected);
        }
        if ("archived".equals(course.get("status"))) throw conflict("COURSE_ARCHIVED","请先恢复课程");
        var asset=asset(admin,input.assetId(),"course_media",input.assetId().equals(old.get("asset_id")));
        if (!(asset.get("duration_seconds") instanceof Number duration) || duration.intValue()<=0 || input.trialSeconds()>duration.intValue())
            throw BusinessException.invalid("试听时长须在实际媒体时长以内");
        if ("published".equals(input.status()) && !"approved".equals(asset.get("review_status")))
            throw conflict("ASSET_NOT_REVIEWED","课节发布前需要审核素材");
        if (create) {
            id=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO content_lesson(id,course_id,title,sort_order,asset_id,trial_seconds,status,version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,1,?,?)",
                id,input.courseId(),input.title(),input.sort(),input.assetId(),input.trialSeconds(),input.status(),now(),now());
        } else {
            jdbc.update("UPDATE content_lesson SET title=?,sort_order=?,asset_id=?,trial_seconds=?,status=?,version=version+1,updated_at=? WHERE id=?",
                input.title(),input.sort(),input.assetId(),input.trialSeconds(),input.status(),now(),id);
        }
        if (input.trialSeconds()>0) {
            jdbc.update("""
                INSERT INTO lesson_trial(asset_id,seconds,state,next_attempt_at) VALUES (?,?,'pending',?)
                ON DUPLICATE KEY UPDATE attempts=CASE WHEN state='failed' THEN 0 ELSE attempts END,
                next_attempt_at=CASE WHEN state='failed' THEN ? ELSE next_attempt_at END,state=CASE WHEN state='failed' THEN 'pending' ELSE state END
                """,input.assetId(),input.trialSeconds(),now(),now());
        }
        jdbc.update("INSERT INTO content_reference_history(lesson_id,course_id,asset_id,teacher_id,category_id,revision) VALUES (?,?,?,?,?,?)",
            id,input.courseId(),input.assetId(),course.get("teacher_id"),course.get("category_id"),create ? 1 : expected+1);
        audit.record(admin.id(),create ? "content.lesson.create" : "content.lesson.update",id,null);
        return catalog.lesson(id,admin,null);
    }
    public void archive(AdminPrincipal admin,String id,CategoryModels.Archive input,boolean lesson) {
        String table=lesson ? "content_lesson" : "content_course";
        if (lesson) {
            var lessonView=catalog.lesson(id,admin,null);
            var course=lock("content_course",lessonView.get("courseId").asText());
            CourseCatalog.scope(admin,(String)course.get("teacher_id"));
        }
        var row=lock(table,id);
        if (!lesson) CourseCatalog.scope(admin,(String)row.get("teacher_id"));
        if ("archived".equals(row.get("status"))) return;
        version(row,input.version());
        jdbc.update("UPDATE "+table+" SET status='archived',version=version+1,updated_at=? WHERE id=?",now(),id);
        audit.record(admin.id(),lesson ? "content.lesson.archive" : "content.course.archive",id,input.reason());
    }
}
