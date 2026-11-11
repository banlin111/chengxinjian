package com.chengxinjian;

import com.chengxinjian.media.MediaStorage;
import com.chengxinjian.user.UserSessionService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:courses;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USERNAME:sa}","spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
    "app.auth.bootstrap-username=integration-admin","app.auth.bootstrap-password=integration-password-12345",
    "app.auth.local-captcha-ticket=integration-captcha-ticket-12345"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CourseIntegrationTest {
    private static final String ADMIN="/api/v1/admin/content/";
    private static final String PLAY="/api/v1/playbacks";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserSessionService sessions;
    @MockitoBean MediaStorage storage;
    String admin,user,uid,teacher,category,cover,audio;
    static String id() { return UUID.randomUUID().toString(); }
    JsonNode http(MockHttpServletRequestBuilder req,String token,Object body,int expected) throws Exception {
        if (token!=null) req.header("Authorization","Bearer "+token);
        if (body!=null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        var response=mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray()).get("data");
    }
    JsonNode create(String resource,Object body) throws Exception {
        return http(post(ADMIN+resource).header("Idempotency-Key",id()),admin,body,200);
    }
    String asset(String owner,String purpose,String type,String review,Integer duration) {
        String id=id();var now=Timestamp.from(Instant.now());
        jdbc.update("""
            INSERT INTO media_asset(id,purpose,filename,mime_type,size_bytes,duration_seconds,sha256,object_key,status,review_status,owner_admin_id,public_url,created_at,updated_at)
            VALUES (?,?,?, ?,20,?,?,?,'ready',?,?,?,?,?)
            """,id,purpose,"fixture",type,duration,"a".repeat(64),"validated/"+id+"/source",review,owner,
            "cover".equals(purpose)?"https://fixture.example/"+id:null,now,now);
        return id;
    }
    @BeforeEach void setup() throws Exception {
        CourseFixtures.clear(jdbc);
        jdbc.update("UPDATE admin_account SET enabled=TRUE,teacher_id=NULL,data_scope='all' WHERE username='integration-admin'");
        jdbc.update("DELETE FROM login_throttle");
        for (String p:List.of("content:read","content:write")) jdbc.update("INSERT INTO admin_role_permission(role_id,permission_code) SELECT 'role-content-manager',? WHERE NOT EXISTS(SELECT 1 FROM admin_role_permission WHERE role_id='role-content-manager' AND permission_code=?)",p,p);
        admin=http(post("/api/v1/admin/auth/login"),null,Map.of("username","integration-admin","password","integration-password-12345","captchaTicket","integration-captcha-ticket-12345"),200).get("accessToken").asText();
        String owner=jdbc.queryForObject("SELECT id FROM admin_account WHERE username='integration-admin'",String.class);
        teacher=create("teachers",json.createObjectNode().put("name","讲师").putNull("avatarAssetId").put("bio","简介").putNull("userId").put("status","published").set("credentials",json.createArrayNode())).get("id").asText();
        category=create("categories",Map.of("name","课程","kind","course","sort",0,"status","published")).get("id").asText();
        cover=asset(owner,"cover","image/jpeg","approved",null); audio=asset(owner,"course_media","audio/mp4","approved",60);
        uid=id();var now=Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO app_user(id,nickname,status,version,created_at,updated_at,last_login_at) VALUES (?,'课程用户','active',1,?,?,?)",uid,now,now,now);
        user=sessions.create(uid).accessToken();
        when(storage.preview(anyString(),any())).thenAnswer(i->"https://private.example/"+i.getArgument(0));
    }
    ObjectNode course(String policy) {
        return json.createObjectNode().put("title","课程").put("summary","简介").put("description","说明")
            .put("coverAssetId",cover).put("categoryId",category).put("teacherId",teacher).put("difficulty","beginner")
            .put("priceFen",policy.contains("purchase")?100:0).putNull("originalPriceFen").put("accessPolicy",policy).put("status","published");
    }
    ObjectNode lesson(String course,int trial) {
        return json.createObjectNode().put("courseId",course).put("title","第一课").put("sort",1).put("assetId",audio).put("trialSeconds",trial).put("status","published");
    }
    JsonNode play(String lesson,String key,int expected) throws Exception {
        return http(post(PLAY).header("Idempotency-Key",key),user,Map.of("target",Map.of("kind","lesson","id",lesson),"platform","wechat_ios"),expected);
    }
    @Test void catalogProjectsPublishedLessonsAndEnforcesParentReferences() throws Exception {
        String c=create("courses",course("free")).get("id").asText();
        create("lessons",lesson(c,0)); create("lessons",lesson(c,0).put("status","draft"));
        var list=http(get("/api/v1/courses").param("minSeconds","60").param("teacherId",teacher),null,null,200);
        var item=list.at("/items/0");
        assertThat(item.get("totalSeconds").asInt()).isEqualTo(60); assertThat(item.get("lessonCount").asInt()).isEqualTo(1);
        assertThat(item.has("coverAssetId")).isFalse();
        var lessons=http(get("/api/v1/courses/"+c+"/lessons"),null,null,200);
        assertThat(lessons.get("total").asInt()).isEqualTo(1); assertThat(lessons.at("/items/0").has("assetId")).isFalse();
        var archive=Map.of("version",1,"reason","撤下");
        http(delete(ADMIN+"teachers/"+teacher),admin,archive,409); http(delete(ADMIN+"categories/"+category),admin,archive,409);
        http(put(ADMIN+"categories/"+category),admin,Map.of("name","噪音","kind","noise","sort",1,"status","published","version",1),409);
        http(delete(ADMIN+"courses/"+c),admin,archive,200);
        http(get("/api/v1/courses/"+c),null,null,404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_reference_history WHERE course_id=?",Long.class,c)).isEqualTo(3);
    }
    @Test void retriesPreserveSnapshotAndCannotReplayAfterTeacherReassignment() throws Exception {
        String key=id(); var body=course("purchase"); var req=post(ADMIN+"courses").header("Idempotency-Key",key);
        var first=http(req,admin,body,200); String c=first.get("id").asText();
        http(put(ADMIN+"courses/"+c),admin,body.deepCopy().put("title","已改").put("version",1),200);
        assertThat(http(req,admin,body,200)).isEqualTo(first);
        http(req,admin,body.deepCopy().put("priceFen",101),409);
        http(put(ADMIN+"courses/"+c),admin,body.deepCopy().put("version",1),409);
        jdbc.update("UPDATE admin_account SET data_scope='own_content',teacher_id=? WHERE username='integration-admin'",teacher);
        String other=id(); var now=Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO content_teacher(id,name,bio,credentials_json,status,created_at,updated_at) VALUES (?,'他人','','[]','published',?,?)",other,now,now);
        http(put(ADMIN+"courses/"+c),admin,body.deepCopy().put("version",2).put("teacherId",other),403);
        jdbc.update("UPDATE content_course SET teacher_id=? WHERE id=?",other,c);
        http(req,admin,body,403);
        assertThat(http(get(ADMIN+"courses"),admin,null,200).get("total").asInt()).isZero();
        http(post(ADMIN+"lessons").header("Idempotency-Key",id()),admin,lesson(c,0),403);
    }
    @Test void mediaReviewAndPurposeAreCheckedOnEveryPublicAccess() throws Exception {
        String c=create("courses",course("free")).get("id").asText();
        http(post(ADMIN+"lessons").header("Idempotency-Key",id()),admin,lesson(c,0).put("assetId",cover),409);
        http(post(ADMIN+"lessons").header("Idempotency-Key",id()),admin,lesson(c,61),400);
        String l=create("lessons",lesson(c,0)).get("id").asText();
        jdbc.update("UPDATE media_asset SET review_status='rejected' WHERE id=?",audio);
        assertThat(http(get("/api/v1/courses/"+c+"/lessons"),null,null,200).get("total").asInt()).isZero();
        play(l,id(),404);
        jdbc.update("UPDATE media_asset SET review_status='rejected',public_url=NULL WHERE id=?",cover);
        http(get("/api/v1/courses/"+c),null,null,404);
    }
    @Test void entitlementWindowsAreSourceSpecificAndMembershipCannotUnlockPurchaseOnly() throws Exception {
        String c=create("courses",course("purchase")).get("id").asText(),l=create("lessons",lesson(c,0)).get("id").asText();
        grant("membership",null,null); play(l,id(),403);
        var expires=Instant.now().plusSeconds(45); grant("course",c,expires);
        var ticket=play(l,id(),200); assertThat(ticket.at("/access/reason").asText()).isEqualTo("purchased");
        assertThat(Instant.parse(ticket.get("expiresAt").asText())).isBeforeOrEqualTo(expires.plusMillis(1));
        http(put(ADMIN+"courses/"+c),admin,course("member").put("version",1),200);
        assertThat(play(l,id(),200).at("/access/reason").asText()).isEqualTo("purchased");
        http(put(ADMIN+"courses/"+c),admin,course("purchase").put("version",2),200);
        jdbc.update("UPDATE entitlement_grant SET revoked_at=CURRENT_TIMESTAMP WHERE kind='course'");
        http(post(PLAY+"/"+ticket.get("playbackId").asText()+"/renew").header("Idempotency-Key",id()),user,null,403);
        grant("course",c,Instant.now().minusSeconds(5)); play(l,id(),403);
        http(put(ADMIN+"courses/"+c),admin,course("member_or_purchase").put("version",3),200);
        assertThat(play(l,id(),200).at("/access/reason").asText()).isEqualTo("member");
        assertThat(http(get("/api/v1/me/entitlements"),user,null,200).get("total").asInt()).isEqualTo(3);
    }
    void grant(String kind,String c,Instant expiry) {
        jdbc.update("INSERT INTO entitlement_grant(id,user_id,kind,target_id,course_id,source,source_id,starts_at,expires_at) VALUES (?,?,?,?,?,'admin',?,?,?)",
            id(),uid,kind,c==null?"plan":c,c,id(),Timestamp.from(Instant.now().minusSeconds(60)),expiry==null?null:Timestamp.from(expiry));
    }
    @Test void trialOnlySignsDerivedKeyAndRevocationAlsoBlocksRenewal() throws Exception {
        String c=create("courses",course("member")).get("id").asText(),l=create("lessons",lesson(c,5)).get("id").asText();
        play(l,id(),403);
        jdbc.update("UPDATE lesson_trial SET state='ready',object_key='trials/clipped.m4a' WHERE asset_id=?",audio);
        String key=id(); var ticket=play(l,key,200); assertThat(play(l,key,200)).isEqualTo(ticket);
        assertThat(ticket.get("url").asText()).endsWith("/trials/clipped.m4a");
        assertThat(ticket.get("durationSeconds").asInt()).isEqualTo(5);
        verify(storage,never()).preview(startsWith("validated/"),any());
        String path=PLAY+"/"+ticket.get("playbackId").asText()+"/renew";
        http(post(path).header("Idempotency-Key",id()),user,null,200);
        http(delete(ADMIN+"lessons/"+l),admin,Map.of("version",1,"reason","归档"),200);
        http(post(path).header("Idempotency-Key",id()),user,null,404);
        play(l,key,404);
    }
    @Test void manualGrantHonorsPermissionPlanDurationAndRevokesOnlyItsOwnSource() throws Exception {
        var planInput=json.createObjectNode().put("name","测试月计划").put("period","month").put("durationDays",17).put("priceFen",100)
            .putNull("originalPriceFen").put("status","published");
        planInput.putArray("benefits").add("测试权益");
        var plan=create("membership-plans",planInput);
        var grantBody=Map.of("kind","membership","targetId",plan.get("id").asText(),"reason","已核准测试授权");
        String path="/api/v1/admin/users/"+uid+"/grants",key=id();
        jdbc.update("DELETE FROM admin_role_permission WHERE role_id='role-content-manager' AND permission_code IN ('users:write','users:read')");
        http(post(path).header("Idempotency-Key",key),admin,grantBody,403);
        jdbc.update("INSERT INTO admin_role_permission VALUES ('role-content-manager','users:write'),('role-content-manager','users:read')");
        try {
            var first=http(post(path).header("Idempotency-Key",key),admin,grantBody,200);
            assertThat(http(post(path).header("Idempotency-Key",key),admin,grantBody,200)).isEqualTo(first);
            var second=http(post(path).header("Idempotency-Key",id()),admin,grantBody,200);
            var membership=http(get("/api/v1/me/membership"),user,null,200);
            assertThat(membership.get("active").asBoolean()).isTrue();
            assertThat(membership.get("planName").asText()).isEqualTo("测试月计划");
            assertThat(java.time.Duration.between(Instant.parse(second.at("/entitlement/startsAt").asText()),Instant.parse(second.at("/entitlement/expiresAt").asText())).toDays()).isEqualTo(17);
            http(post("/api/v1/admin/grants/"+first.get("id").asText()+"/revoke").header("Idempotency-Key",id()),admin,Map.of("reason","测试撤销"),200);
            assertThat(http(get("/api/v1/me/membership"),user,null,200).get("active").asBoolean()).isTrue();
            http(delete(ADMIN+"membership-plans/"+plan.get("id").asText()),admin,Map.of("reason","停售","version",1),200);
            assertThat(http(get("/api/v1/membership/plans"),null,null,200)).isEmpty();
            assertThat(http(get(path),admin,null,200).get("total").asInt()).isEqualTo(2);
            http(post(path).header("Idempotency-Key",id()),admin,grantBody,404);
        } finally { jdbc.update("DELETE FROM admin_role_permission WHERE role_id='role-content-manager' AND permission_code IN ('users:write','users:read')"); }
    }

    @Test void seekDeduplicationAndResumeUseServerProgress() throws Exception {
        String c=create("courses",course("free")).get("id").asText(),l=create("lessons",lesson(c,0)).get("id").asText();
        var ticket=play(l,id(),200);String playback=ticket.get("playbackId").asText(),key=id();
        var route=post(PLAY+"/"+playback+"/events").header("Idempotency-Key",key);
        var body=json.createObjectNode().put("eventId",id()).put("sequence",1).put("kind","seek").put("positionSeconds",30)
            .put("playedDeltaSeconds",30).put("speed",1).put("occurredAt",Instant.now().toString());
        var ack=http(route,user,body,200);
        assertThat(ack.get("creditedSeconds").asInt()).isZero(); assertThat(http(route,user,body,200)).isEqualTo(ack);
        http(route,user,body.deepCopy().put("positionSeconds",31),409);
        assertThat(play(l,id(),200).get("resumeSeconds").asInt()).isEqualTo(30);
        jdbc.update("UPDATE lesson_playback SET last_event_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(8)),playback);
        jdbc.update("UPDATE lesson_progress SET last_played_at=? WHERE user_id=?",Timestamp.from(Instant.now().minusSeconds(8)),uid);
        var next=http(post(PLAY+"/"+playback+"/events").header("Idempotency-Key",id()),user,body.deepCopy().put("eventId",id()).put("sequence",2)
            .put("kind","playing").put("positionSeconds",35),200);
        assertThat(next.get("creditedSeconds").asInt()).isEqualTo(5);
        assertThat(http(get("/api/v1/courses/"+c),user,null,200).get("progressPercent").asInt()).isEqualTo(8);
    }

    @Test void libraryPersistsAcrossSessionsAndRepeatedWritesDoNotDuplicate() throws Exception {
        String c=create("courses",course("free")).get("id").asText(),l=create("lessons",lesson(c,0)).get("id").asText();
        String favorite="/api/v1/me/favorites/course/"+c,like="/api/v1/me/likes/course/"+c;
        http(put(favorite),user,Map.of("favorite",true),200);
        var saved=http(get("/api/v1/me/favorites"),user,null,200).at("/items/0/savedAt");
        http(put(favorite),user,Map.of("favorite",true),200);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var tasks=new ArrayList<java.util.concurrent.Callable<JsonNode>>();
            for(int n=0;n<4;n++)tasks.add(()->http(put(like),user,Map.of("liked",true),200));
            for(var f:pool.invokeAll(tasks))assertThat(f.get().get("count").asInt()).isEqualTo(1);
        }
        String newSession=sessions.create(uid).accessToken();
        var result=http(get("/api/v1/me/favorites"),newSession,null,200);
        assertThat(result.get("total").asInt()).isEqualTo(1);assertThat(result.at("/items/0/savedAt")).isEqualTo(saved);
        var course=http(get("/api/v1/courses/"+c),newSession,null,200);
        assertThat(course.get("favorite").asBoolean()).isTrue();assertThat(course.get("liked").asBoolean()).isTrue();assertThat(course.get("likeCount").asInt()).isEqualTo(1);
        assertThat(http(get("/api/v1/courses/"+c),null,null,200).get("favorite").asBoolean()).isFalse();
        http(put("/api/v1/me/favorites/lesson/"+l),user,Map.of("favorite",true),200);
        assertThat(http(get("/api/v1/me/favorites").param("kind","lesson"),user,null,200).get("total").asInt()).isEqualTo(1);
        http(put(like),newSession,Map.of("liked",false),200);
        assertThat(http(get("/api/v1/courses/"+c),null,null,200).get("likeCount").asInt()).isZero();
    }
    @Test void hiddenContentIsExcludedButCanBeRemovedFromLibrary() throws Exception {
        String c=create("courses",course("purchase")).get("id").asText();
        String path="/api/v1/me/favorites/course/"+c;
        http(put(path),user,Map.of("favorite",true),200);
        http(delete(ADMIN+"courses/"+c),admin,Map.of("version",1,"reason","归档"),200);
        assertThat(http(get("/api/v1/me/favorites"),user,null,200).get("total").asInt()).isZero();
        http(put(path),user,Map.of("favorite",true),404);
        http(put(path),user,Map.of("favorite",false),200);
        assertThat(jdbc.queryForObject("SELECT favorite FROM user_content_relation WHERE user_id=? AND target_id=?",Boolean.class,uid,c)).isFalse();
        http(put("/api/v1/me/favorites/noise/"+c),user,Map.of("favorite",true),400);
        http(put("/api/v1/me/favorites/replay/"+c),user,Map.of("favorite",true),503);
        http(get("/api/v1/me/favorites"),admin,null,403);
        http(get("/api/v1/me/favorites"),null,null,401);
        http(get("/api/v1/me/favorites").param("pageSize","101"),user,null,400);
    }
    @Test void learningUsesEventsAndSurvivesRevocationWithoutGrantingPlayback() throws Exception {
        String c=create("courses",course("purchase")).get("id").asText(),l=create("lessons",lesson(c,0)).get("id").asText();
        grant("course",c,null);String playback=play(l,id(),200).get("playbackId").asText();
        jdbc.update("UPDATE lesson_playback SET last_event_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(20)),playback);
        var event=json.createObjectNode().put("eventId",id()).put("sequence",1).put("kind","playing").put("positionSeconds",10)
            .put("playedDeltaSeconds",10).put("speed",1).put("occurredAt",Instant.now().toString());
        String key=id();http(post(PLAY+"/"+playback+"/events").header("Idempotency-Key",key),user,event,200);
        http(post(PLAY+"/"+playback+"/events").header("Idempotency-Key",key),user,event,200);
        String nextSession=sessions.create(uid).accessToken();
        var list=http(get("/api/v1/me/learning").param("state","in_progress"),nextSession,null,200);
        assertThat(list.get("total").asInt()).isEqualTo(1);assertThat(list.at("/items/0/positionSeconds").asInt()).isEqualTo(10);
        assertThat(list.at("/items/0/progressPercent").asInt()).isEqualTo(16);assertThat(list.toString()).doesNotContain("validated/","object_key","url");
        assertThat(play(l,id(),200).get("resumeSeconds").asInt()).isEqualTo(10);
        assertThat(http(get("/api/v1/me/learning").param("state","completed"),user,null,200).get("total").asInt()).isZero();
        assertThat(http(get("/api/v1/me/learning").param("state","purchased"),user,null,200).get("total").asInt()).isEqualTo(1);
        jdbc.update("UPDATE entitlement_grant SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=?",uid);
        assertThat(http(get("/api/v1/me/learning"),user,null,200).get("total").asInt()).isEqualTo(1);
        assertThat(http(get("/api/v1/me/learning").param("state","purchased"),user,null,200).get("total").asInt()).isZero();
        play(l,id(),403);
        jdbc.update("UPDATE app_user SET status='banned' WHERE id=?",uid);
        http(get("/api/v1/me/learning"),user,null,403);
    }
    @Test void learningAssetReplacementAndConcurrentEventsDoNotReuseOrDoubleCredit() throws Exception {
        String c=create("courses",course("free")).get("id").asText(),l=create("lessons",lesson(c,0)).get("id").asText();
        String p1=play(l,id(),200).get("playbackId").asText(),p2=play(l,id(),200).get("playbackId").asText();
        jdbc.update("UPDATE lesson_playback SET last_event_at=? WHERE user_id=?",Timestamp.from(Instant.now().minusSeconds(10)),uid);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks=new ArrayList<java.util.concurrent.Callable<JsonNode>>();
            for(String playback:List.of(p1,p2))tasks.add(()->http(post(PLAY+"/"+playback+"/events").header("Idempotency-Key",id()),user,
                Map.of("eventId",id(),"sequence",1,"kind","playing","positionSeconds",10,"playedDeltaSeconds",10,"speed",1,"occurredAt",Instant.now().toString()),200));
            for(var f:pool.invokeAll(tasks))f.get();
        }
        assertThat(jdbc.queryForObject("SELECT credited_seconds FROM lesson_progress WHERE user_id=?",Integer.class,uid)).isEqualTo(10);
        assertThat(http(get("/api/v1/me/learning"),user,null,200).get("total").asInt()).isEqualTo(1);
        String owner=jdbc.queryForObject("SELECT id FROM admin_account WHERE username='integration-admin'",String.class);
        String replacement=asset(owner,"course_media","audio/mp4","approved",120);
        http(put(ADMIN+"lessons/"+l),admin,lesson(c,0).put("assetId",replacement).put("version",1),200);
        assertThat(http(get("/api/v1/me/learning"),user,null,200).get("total").asInt()).isZero();
        assertThat(play(l,id(),200).get("resumeSeconds").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_progress WHERE user_id=?",Long.class,uid)).isEqualTo(1);
    }
}
