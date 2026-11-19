package com.chengxinjian.catalog;

import com.chengxinjian.common.*;
import com.chengxinjian.user.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static com.chengxinjian.catalog.CategoryModels.Page;

@RestController
@Validated
@RequestMapping("/api/v1/me")
@Transactional(isolation=Isolation.READ_COMMITTED)
public class LearningLibrary {
    public record Favorite(@NotNull Boolean favorite) {}
    public record Like(@NotNull Boolean liked) {}
    private final JdbcTemplate jdbc; private final UserService users; private final CourseCatalog catalog;
    private final Clock clock; private final ObjectMapper json;
    private final LearningHistory history; private final PracticeAudioCatalog audio;
    public LearningLibrary(JdbcTemplate jdbc,UserService users,CourseCatalog catalog,Clock clock,ObjectMapper json,LearningHistory history,PracticeAudioCatalog audio) {
        this.audio=audio;
        this.jdbc=jdbc;this.users=users;this.catalog=catalog;this.clock=clock;this.json=json;this.history=history;
    }
    private void kind(String kind) {
        if (!Set.of("course","lesson","meditation","replay").contains(kind)) throw BusinessException.invalid("内容类型无效");
        if (!Set.of("course","lesson","meditation").contains(kind)) throw new BusinessException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"FEATURE_UNAVAILABLE","此内容类型尚未接入");
    }
    private void visible(UserPrincipal user,String kind,String id) {
        if("course".equals(kind)) catalog.course(id,null,user); else if ("meditation".equals(kind)) audio.detail(kind,id,user,null); else catalog.lesson(id,null,user);
    }
    @PutMapping("/favorites/{kind}/{id}")
    ApiResponse<com.chengxinjian.auth.AdminAuthController.OK> favorite(@AuthenticationPrincipal UserPrincipal user,@PathVariable String kind,@PathVariable @Size(max=36) String id,@RequestBody @Valid Favorite body) {
        change(user,kind,id,body.favorite(),null);
        return ApiResponse.ok(new com.chengxinjian.auth.AdminAuthController.OK(true));
    }
    @PutMapping("/likes/{kind}/{id}")
    ApiResponse<Map<String,Object>> like(@AuthenticationPrincipal UserPrincipal user,@PathVariable String kind,@PathVariable @Size(max=36) String id,@RequestBody @Valid Like body) {
        change(user,kind,id,null,body.liked());
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM user_content_relation WHERE kind=? AND target_id=? AND liked=TRUE",Long.class,kind,id);
        return ApiResponse.ok(Map.of("liked",body.liked(),"count",count));
    }
    private void change(UserPrincipal user,String kind,String id,Boolean favorite,Boolean liked) {
        kind(kind); users.requireActive(user.id(),true);
        // Removing a relation remains possible after archival; adding requires current catalog visibility.
        if(Boolean.TRUE.equals(favorite) || Boolean.TRUE.equals(liked)) visible(user,kind,id);
        var rows=jdbc.queryForList("SELECT favorite,liked,saved_at FROM user_content_relation WHERE user_id=? AND kind=? AND target_id=?",user.id(),kind,id);
        if(rows.isEmpty()) {
            if(!Boolean.TRUE.equals(favorite) && !Boolean.TRUE.equals(liked)) return;
            jdbc.update("INSERT INTO user_content_relation(user_id,kind,target_id,favorite,liked,saved_at) VALUES (?,?,?,?,?,?)",user.id(),kind,id,Boolean.TRUE.equals(favorite),Boolean.TRUE.equals(liked),Timestamp.from(clock.instant()));
        } else {
            var old=rows.getFirst(); boolean f=favorite==null ? (Boolean)old.get("favorite") : favorite;
            boolean l=liked==null ? (Boolean)old.get("liked") : liked;
            var saved=f && !(Boolean)old.get("favorite") ? Timestamp.from(clock.instant()) : old.get("saved_at");
            jdbc.update("UPDATE user_content_relation SET favorite=?,liked=?,saved_at=? WHERE user_id=? AND kind=? AND target_id=?",f,l,saved,user.id(),kind,id);
        }
    }
    @GetMapping("/favorites")
    ApiResponse<Page<ObjectNode>> favorites(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required=false) String kind,@RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        users.requireActive(user.id(),false); if(kind!=null) kind(kind);
        // Both branches use the same visibility predicates as public catalog; private media keys never appear.
        String union="SELECT r.kind,r.target_id,c.title,cover.public_url,r.saved_at FROM user_content_relation r JOIN content_course c ON c.id=r.target_id " +
            "JOIN content_teacher t ON t.id=c.teacher_id JOIN content_category k ON k.id=c.category_id JOIN media_asset cover ON cover.id=c.cover_asset_id WHERE r.user_id=? AND r.favorite=TRUE AND r.kind='course' AND "+CourseCatalog.COURSE_VISIBLE+
            " UNION ALL SELECT r.kind,r.target_id,l.title,cover.public_url,r.saved_at FROM user_content_relation r JOIN content_lesson l ON l.id=r.target_id JOIN media_asset a ON a.id=l.asset_id "+
            "JOIN content_course c ON c.id=l.course_id JOIN content_teacher t ON t.id=c.teacher_id JOIN content_category k ON k.id=c.category_id JOIN media_asset cover ON cover.id=c.cover_asset_id WHERE r.user_id=? AND r.favorite=TRUE AND r.kind='lesson' AND "+CourseCatalog.COURSE_VISIBLE+" AND "+CourseCatalog.LESSON_VISIBLE+
            " UNION ALL SELECT r.kind,r.target_id,c.title,cover.public_url,r.saved_at "+PracticeAudioCatalog.FROM+
            " JOIN user_content_relation r ON r.target_id=c.id WHERE r.user_id=? AND r.favorite=TRUE AND r.kind='meditation' AND c.kind='meditation' AND "+PracticeAudioCatalog.VISIBLE;
        String from=" FROM ("+union+") saved"+(kind==null?"":" WHERE kind=?");
        List<Object> args=new ArrayList<>(List.of(user.id(),user.id(),user.id())); if(kind!=null)args.add(kind);
        long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());args.add(pageSize);args.add(((long)page-1)*pageSize);
        var items=jdbc.query("SELECT *"+from+" ORDER BY saved_at DESC,kind,target_id LIMIT ? OFFSET ?",(r,n)->{
            var result=json.createObjectNode().put("title",r.getString("title")).put("coverUrl",r.getString("public_url")).put("savedAt",r.getTimestamp("saved_at").toInstant().toString());
            result.putObject("target").put("kind",r.getString("kind")).put("id",r.getString("target_id"));return result;
        },args.toArray());
        return ApiResponse.ok(new Page<>(items,page,pageSize,total));
    }
    @GetMapping("/learning")
    ApiResponse<Page<ObjectNode>> learning(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required=false) @Pattern(regexp="in_progress|completed|purchased") String state,
            @RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        users.requireActive(user.id(),false);
        return ApiResponse.ok(history.page(user.id(),state,page,pageSize));
    }
}
