package com.chengxinjian;

import com.chengxinjian.user.UserSessionService;
import com.fasterxml.jackson.databind.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:notices;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USERNAME:sa}","spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
    "app.auth.bootstrap-username=notice-admin","app.auth.bootstrap-password=integration-password-12345",
    "app.auth.local-captcha-ticket=integration-captcha-ticket-12345"})
@AutoConfigureMockMvc @ActiveProfiles("local")
class NoticeIntegrationTest {
    private static final String BASE="/api/v1/admin/content/notices";
    private static final Instant NOW=Instant.parse("2026-11-19T08:00:00Z");
    static class TestClock extends Clock {
        volatile Instant now=NOW;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
        public Instant instant(){return now;}
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary TestClock noticeClock(){return new TestClock();}
    }
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired UserSessionService sessions; @Autowired TestClock clock;
    String admin,actor,role,userId,otherId,user,other;
    String id(){return UUID.randomUUID().toString();}
    JsonNode http(MockHttpServletRequestBuilder request,String token,Object body,int expected)throws Exception {
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return json.readTree(mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray());
    }
    JsonNode data(MockHttpServletRequestBuilder request,String token,Object body)throws Exception {
        return http(request,token,body,200).get("data");
    }
    Map<String,Object> body(String title,String status,String at){
        return new HashMap<>(Map.of("title",title,"content","公告正文\n请留意课程安排。","status",status,"publishedAt",at));
    }
    JsonNode create(String title,String status,String at)throws Exception {
        return data(post(BASE).header("Idempotency-Key",id()),admin,body(title,status,at));
    }
    String user(String id){
        var now=Timestamp.from(NOW);
        jdbc.update("INSERT INTO app_user(id,nickname,created_at,updated_at,last_login_at) VALUES (?,'公告测试',?,?,?)",id,now,now,now);
        return sessions.create(id).accessToken();
    }
    @BeforeEach void setup()throws Exception {
        clock.now=NOW;
        actor=jdbc.queryForObject("SELECT id FROM admin_account WHERE username='notice-admin'",String.class);
        jdbc.update("UPDATE admin_account SET enabled=TRUE,data_scope='all' WHERE id=?",actor);
        jdbc.update("DELETE FROM login_throttle");
        role=id();
        jdbc.update("INSERT INTO admin_role(id,name) VALUES (?,?)",role,"公告测试"+role);
        jdbc.update("DELETE FROM admin_account_role WHERE account_id=?",actor);
        jdbc.update("INSERT INTO admin_account_role VALUES (?,?)",actor,role);
        jdbc.update("INSERT INTO admin_role_permission VALUES (?,'content:read'),(?,'content:write')",role,role);
        admin=data(post("/api/v1/admin/auth/login"),null,Map.of("username","notice-admin","password","integration-password-12345","captchaTicket","integration-captcha-ticket-12345")).path("accessToken").asText();
        jdbc.update("DELETE FROM user_notice_read");jdbc.update("DELETE FROM content_notice");
        userId=id();otherId=id();user=user(userId);other=user(otherId);
    }
    @AfterEach void cleanup(){
        jdbc.update("DELETE FROM user_notice_read");jdbc.update("DELETE FROM content_notice");
        jdbc.update("DELETE FROM user_auth_session WHERE user_id IN (?,?)",userId,otherId);
        jdbc.update("DELETE FROM app_user WHERE id IN (?,?)",userId,otherId);
        jdbc.update("DELETE FROM admin_account_role WHERE role_id=?",role);
        jdbc.update("DELETE FROM admin_role_permission WHERE role_id=?",role);jdbc.update("DELETE FROM admin_role WHERE id=?",role);
        jdbc.update("UPDATE admin_account SET enabled=TRUE,data_scope='all' WHERE id=?",actor);
    }
    @Test void schedulesUseServerTimeAndAllPublicSurfacesHideUnpublishedContent()throws Exception {
        var live=create("当前公告","published",NOW.toString());
        var future=create("预约公告","published","2026-11-19T16:00:01+08:00");
        var draft=create("草稿","draft",NOW.minusSeconds(10).toString());
        var archived=create("旧公告","archived",NOW.minusSeconds(20).toString());
        assertThat(data(get("/api/v1/notices"),null,null).path("total").asInt()).isEqualTo(1);
        assertThat(data(get("/api/v1/home"),null,null).path("notices").get(0).path("id")).isEqualTo(live.path("id"));
        for(var hidden:List.of(future,draft,archived)){
            http(get("/api/v1/notices/"+hidden.path("id").asText()),null,null,404);
            http(put("/api/v1/me/notices/"+hidden.path("id").asText()+"/read"),user,null,404);
        }
        clock.now=NOW.plusSeconds(1);
        var page=data(get("/api/v1/notices"),null,null);
        assertThat(page.path("total").asInt()).isEqualTo(2);
        assertThat(page.path("items").get(0).path("id")).isEqualTo(future.path("id"));
        assertThat(page.path("items").get(0).path("publishedAt").asText()).isEqualTo(clock.now.toString());
        assertThat(data(get("/api/v1/home"),null,null).path("notices").size()).isEqualTo(2);
    }
    @Test void receiptsArePrivatePersistentAndInvalidateWhenTheNoticeChanges()throws Exception {
        var notice=create("须知","published",NOW.toString());String target=notice.path("id").asText();
        data(put("/api/v1/me/notices/"+target+"/read"),user,null);
        String nextSession=sessions.create(userId).accessToken();
        assertThat(data(get("/api/v1/notices/"+target),nextSession,null).path("read").asBoolean()).isTrue();
        assertThat(data(get("/api/v1/notices"),nextSession,null).path("items").get(0).path("read").asBoolean()).isTrue();
        assertThat(data(get("/api/v1/home"),nextSession,null).path("notices").get(0).path("read").asBoolean()).isTrue();
        for(String token:Arrays.asList(null,other))assertThat(data(get("/api/v1/notices/"+target),token,null).path("read").asBoolean()).isFalse();
        http(put("/api/v1/me/notices/"+target+"/read"),null,null,401);
        http(put("/api/v1/me/notices/"+target+"/read"),admin,null,403);
        var edit=body("须知修订","published",NOW.toString());edit.put("version",1);
        data(put(BASE+"/"+target),admin,edit);
        assertThat(data(get("/api/v1/notices/"+target),user,null).path("read").asBoolean()).isFalse();
        data(put("/api/v1/me/notices/"+target+"/read"),user,null);
        assertThat(jdbc.queryForObject("SELECT notice_version FROM user_notice_read WHERE user_id=? AND notice_id=?",Integer.class,userId,target)).isEqualTo(2);
        assertThat(data(get("/api/v1/notices/"+target),user,null).fieldNames()).toIterable().containsExactlyInAnyOrder("id","title","content","publishedAt","read");
    }
    @Test void concurrentAcknowledgementsAreIdempotentAndDoNotChangeFirstReadTime()throws Exception {
        String target=create("并发已读","published",NOW.toString()).path("id").asText();
        var start=new CountDownLatch(1);var ready=new CountDownLatch(4);
        try(var pool=Executors.newFixedThreadPool(4)){
            var futures=new ArrayList<Future<JsonNode>>();
            for(int i=0;i<4;i++)futures.add(pool.submit(()->{ready.countDown();start.await();return data(put("/api/v1/me/notices/"+target+"/read"),user,null);}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            for(var future:futures)assertThat(future.get(20,TimeUnit.SECONDS).path("success").asBoolean()).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notice_read WHERE notice_id=?",Integer.class,target)).isEqualTo(1);
        clock.now=NOW.plusSeconds(10);data(put("/api/v1/me/notices/"+target+"/read"),user,null);
        assertThat(jdbc.queryForObject("SELECT read_at FROM user_notice_read WHERE notice_id=?",Timestamp.class,target).toInstant()).isEqualTo(NOW);
    }
    @Test void archiveHidesNoticeAndPreservesReceiptsWithSingleAuditEvent()throws Exception {
        String target=create("归档","published",NOW.toString()).path("id").asText();
        data(put("/api/v1/me/notices/"+target+"/read"),user,null);
        var stale=Map.of("version",2,"reason","撤回");
        assertThat(http(delete(BASE+"/"+target),admin,stale,409).path("code").asText()).isEqualTo("VERSION_CONFLICT");
        var request=Map.of("version",1,"reason","内容已过期");
        data(delete(BASE+"/"+target),admin,request);data(delete(BASE+"/"+target),admin,request);
        http(get("/api/v1/notices/"+target),user,null,404);http(put("/api/v1/me/notices/"+target+"/read"),user,null,404);
        assertThat(data(get("/api/v1/notices"),user,null).path("total").asInt()).isZero();
        assertThat(data(get("/api/v1/home"),user,null).path("notices").size()).isZero();
        assertThat(data(get(BASE+"/"+target),admin,null).path("status").asText()).isEqualTo("archived");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notice_read WHERE notice_id=?",Integer.class,target)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reason FROM audit_log WHERE target_id=? AND action='notice.archive'",String.class,target)).isEqualTo("内容已过期");
    }
    @Test void replayChecksCurrentScopeAndPermissionAndRejectsDifferentBody()throws Exception {
        var input=body("幂等创建","draft",NOW.toString());String key=id();
        var first=data(post(BASE).header("Idempotency-Key",key),admin,input);
        assertThat(data(post(BASE).header("Idempotency-Key",key),admin,input)).isEqualTo(first);
        var changed=new HashMap<>(input);changed.put("title","不同内容");
        assertThat(http(post(BASE).header("Idempotency-Key",key),admin,changed,409).path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        jdbc.update("UPDATE admin_account SET data_scope='own_content' WHERE id=?",actor);
        http(post(BASE).header("Idempotency-Key",key),admin,input,403);
        http(get(BASE),admin,null,403);http(get(BASE+"/"+first.path("id").asText()),admin,null,403);
        jdbc.update("UPDATE admin_account SET data_scope='all' WHERE id=?",actor);
        jdbc.update("DELETE FROM admin_role_permission WHERE role_id=? AND permission_code='content:write'",role);
        http(post(BASE).header("Idempotency-Key",key),admin,input,403);
        http(get(BASE),admin,null,200);http(get(BASE),user,null,401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_notice",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE target_id=? AND action='notice.create'",Integer.class,first.path("id").asText())).isEqualTo(1);
    }
    @Test void competingEditsHaveOneWinnerAndInvalidPayloadsLeaveNoRows()throws Exception {
        String target=create("原版","draft",NOW.toString()).path("id").asText();
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var futures=new ArrayList<Future<Integer>>();
            for(String title:List.of("甲","乙"))futures.add(pool.submit(()->{
                var edit=body(title,"published",NOW.toString());edit.put("version",1);
                ready.countDown();start.await();
                return mvc.perform(put(BASE+"/"+target).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(edit))).andReturn().getResponse().getStatus();
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(data(get(BASE+"/"+target),admin,null).path("version").asInt()).isEqualTo(2);
        for(var change:List.of(Map.entry("title",(Object)" "),Map.entry("content",(Object)"x".repeat(10001)),
                Map.entry("publishedAt",(Object)"1969-12-31T23:59:59Z"),Map.entry("publishedAt",(Object)"2026-11-19 10:00"),
                Map.entry("status",(Object)"deleted"),Map.entry("unexpected",(Object)true))){
            var input=body("非法请求","draft",NOW.toString());input.put(change.getKey(),change.getValue());
            http(post(BASE).header("Idempotency-Key",id()),admin,input,400);
        }
        http(post(BASE).header("Idempotency-Key","invalid"),admin,body("非法幂等键","draft",NOW.toString()),400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_notice",Integer.class)).isEqualTo(1);
    }
    @Test void paginationLiteralSearchAndUnsupportedFiltersAreConsistent()throws Exception {
        for(String title:List.of("公告100%_!","公告普通","公告最后","公告草稿"))create(title,title.equals("公告草稿")?"draft":"published",NOW.toString());
        var filtered=data(get(BASE).param("q","%_!").param("status","published"),admin,null);
        assertThat(filtered.path("total").asInt()).isEqualTo(1);
        var first=data(get("/api/v1/notices").param("pageSize","2"),null,null);
        var second=data(get("/api/v1/notices").param("pageSize","2").param("page","2"),null,null);
        assertThat(first.path("total").asInt()).isEqualTo(3);assertThat(second.path("items").size()).isEqualTo(1);
        assertThat(first.path("items").findValuesAsText("id")).doesNotContain(second.path("items").get(0).path("id").asText());
        assertThat(data(get("/api/v1/home"),null,null).path("notices").size()).isEqualTo(3);
        http(get("/api/v1/notices").param("page","0"),null,null,400);
        http(get("/api/v1/notices").param("pageSize","101"),null,null,400);
        http(get(BASE).param("courseId",id()),admin,null,400);
        http(get(BASE).param("status","invalid"),admin,null,400);
    }
    @Test void blockedOrRevokedUsersCannotAcknowledgeNotice()throws Exception {
        String target=create("账号状态","published",NOW.toString()).path("id").asText();
        jdbc.update("UPDATE app_user SET status='banned' WHERE id=?",userId);
        http(put("/api/v1/me/notices/"+target+"/read"),user,null,403);
        jdbc.update("UPDATE user_auth_session SET revoked=TRUE WHERE user_id=?",otherId);
        http(put("/api/v1/me/notices/"+target+"/read"),other,null,401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notice_read",Integer.class)).isZero();
    }
}
