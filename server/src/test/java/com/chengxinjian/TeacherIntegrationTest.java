package com.chengxinjian;

import com.chengxinjian.user.UserModels.Settings;
import com.chengxinjian.user.UserSessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:teachers;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USERNAME:sa}","spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
    "app.auth.bootstrap-username=integration-admin","app.auth.bootstrap-password=integration-password-12345",
    "app.auth.local-captcha-ticket=integration-captcha-ticket-12345"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TeacherIntegrationTest {
    private static final String BASE="/api/v1/admin/content/teachers";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserSessionService sessions;
    @BeforeEach void reset() {
        jdbc.update("UPDATE admin_account SET teacher_id=NULL,enabled=TRUE,data_scope='all'");
        for (var table:List.of("media_processing_job","media_upload_request","teacher_follow","content_teacher","media_asset","user_agreement_consent","user_auth_session","wechat_identity","user_settings","app_user","wechat_code_use","login_throttle","audit_log","auth_session","idempotency_record")) { jdbc.update("DELETE FROM "+table); }
        jdbc.update("DELETE FROM admin_role_permission WHERE role_id='role-content-manager'");
        for (var permission:List.of("content:read","content:write")) {
            jdbc.update("INSERT INTO admin_role_permission(role_id,permission_code) VALUES ('role-content-manager',?)",permission);
        }
    }
    private MvcResult send(MockHttpServletRequestBuilder r,Object body,int status) throws Exception {
        if (body!=null) { r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)); }
        return mvc.perform(r).andExpect(status().is(status)).andReturn();
    }
    private JsonNode data(MvcResult r) throws Exception { return json.readTree(r.getResponse().getContentAsByteArray()).get("data"); }
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r,String token) { return r.header("Authorization","Bearer "+token); }
    private String admin() throws Exception {
        return data(send(post("/api/v1/admin/auth/login"),Map.of("username","integration-admin","password","integration-password-12345","captchaTicket","integration-captcha-ticket-12345"),200)).get("accessToken").asText();
    }
    private Map<String,Object> input(String name,String status) {
        var body=new LinkedHashMap<String,Object>();body.put("name",name);body.put("bio","讲师介绍");body.put("credentials",List.of("经核实的资历"));
        body.put("avatarAssetId",null);body.put("userId",null);body.put("status",status);return body;
    }
    private JsonNode create(String token,Map<String,Object> body) throws Exception {
        return data(send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),body,200));
    }
    private Map<String,Object> update(Map<String,Object> body,int version) { var result=new LinkedHashMap<>(body);result.put("version",version);return result; }
    private String user() throws Exception {
        String id=UUID.randomUUID().toString();var now=Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO app_user(id,nickname,status,version,created_at,updated_at,last_login_at) VALUES (?,'测试用户','active',1,?,?,?)",id,now,now,now);
        jdbc.update("INSERT INTO user_settings(user_id,settings_json,version) VALUES (?,?,1)",id,json.writeValueAsString(Settings.defaults()));return id;
    }
    private String avatar(String owner,String purpose,String status) {
        String id=UUID.randomUUID().toString();var now=Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO media_asset(id,purpose,filename,mime_type,size_bytes,sha256,object_key,status,owner_admin_id,public_url,created_at,updated_at) VALUES (?,?,?,'image/png',128,?,?,?,?,?,?,?)",
            id,purpose,"test.png","a".repeat(64),"test/"+id,status,owner,"https://assets.example.test/"+id,now,now);return id;
    }
    @Test void publicCatalogOnlyShowsPublishedTeachersAndNeverAccountOrAssetIdentifiers() throws Exception {
        String token=admin();var draft=create(token,input("草稿","draft"));var published=create(token,input("公开讲师","published"));
        var page=data(send(get("/api/v1/teachers"),null,200));assertThat(page.get("total").asInt()).isEqualTo(1);
        assertThat(page.get("items").get(0).get("id")).isEqualTo(published.get("id"));
        var detail=data(send(get("/api/v1/teachers/"+published.get("id").asText()),null,200));
        assertThat(detail.has("userId")).isFalse();assertThat(detail.has("avatarAssetId")).isFalse();assertThat(detail.get("followed").asBoolean()).isFalse();
        send(get("/api/v1/teachers/"+draft.get("id").asText()),null,404);
        send(as(get(BASE),token),null,200);send(get(BASE),null,401);
    }
    @Test void createRetriesPreserveOriginalSnapshotAndDifferentPayloadConflicts() throws Exception {
        String token=admin(),key=UUID.randomUUID().toString();var body=input("原资料","draft");
        var first=data(send(as(post(BASE).header("Idempotency-Key",key),token),body,200));String id=first.get("id").asText();
        send(as(put(BASE+"/"+id),token),update(input("修改后","published"),1),200);
        assertThat(data(send(as(post(BASE).header("Idempotency-Key",key),token),body,200))).isEqualTo(first);
        send(as(post(BASE).header("Idempotency-Key",key),token),input("不同资料","draft"),409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_teacher",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='content.teacher.create'",Long.class)).isEqualTo(1);
    }
    @Test void concurrentCreatesWithSameKeyCreateOneProfile() throws Exception {
        String token=admin(),key=UUID.randomUUID().toString();var body=input("并发","draft");
        Callable<JsonNode> call=()->data(send(as(post(BASE).header("Idempotency-Key",key),token),body,200));
        try (var pool=Executors.newFixedThreadPool(2)) {var a=pool.submit(call);var b=pool.submit(call);assertThat(a.get()).isEqualTo(b.get());}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_teacher",Long.class)).isEqualTo(1);
    }
    @Test void staleAndConcurrentUpdatesNeverOverwriteNewerVersions() throws Exception {
        String token=admin(),id=create(token,input("初始","draft")).get("id").asText();
        Callable<Integer> call=()->mvc.perform(as(put(BASE+"/"+id),token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(update(input("并发修改","published"),1)))).andReturn().getResponse().getStatus();
        try (var pool=Executors.newFixedThreadPool(2)) {var a=pool.submit(call);var b=pool.submit(call);assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(200,409);}
        assertThat(data(send(as(get(BASE+"/"+id),token),null,200)).get("version").asInt()).isEqualTo(2);
    }
    @Test void ownContentCanOnlyReadAndEditItsBoundTeacherWithoutRebindingAccounts() throws Exception {
        String token=admin(),id=create(token,input("本人","draft")).get("id").asText(),other=create(token,input("他人","published")).get("id").asText();
        jdbc.update("UPDATE admin_account SET data_scope='own_content',teacher_id=?",id);
        var list=data(send(as(get(BASE),token),null,200));assertThat(list.get("total").asInt()).isEqualTo(1);
        send(as(get(BASE+"/"+other),token),null,403);send(as(get(BASE+"?teacherId="+other),token),null,403);
        send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),input("新增越权","draft"),403);
        send(as(put(BASE+"/"+id),token),update(input("本人更新","published"),1),200);
        var rebind=update(input("本人更新","published"),2);rebind.put("userId",user());send(as(put(BASE+"/"+id),token),rebind,403);
        send(as(put(BASE+"/"+other),token),update(input("越权","draft"),1),403);
        send(as(delete(BASE+"/"+other),token),Map.of("version",1,"reason","越权"),403);
    }
    @Test void userBindingIsUniqueAndTeacherRoleChangesImmediatelyWithoutBecomingAdmin() throws Exception {
        String token=admin(),id=user(),userToken=sessions.create(id).accessToken();var body=input("已关联","published");body.put("userId",id);
        var teacher=create(token,body);String teacherId=teacher.get("id").asText();
        assertThat(data(send(as(get("/api/v1/me"),userToken),null,200)).get("roles").toString()).contains("teacher");
        send(as(get(BASE),userToken),null,401);
        send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),body,409);
        send(as(delete(BASE+"/"+teacherId),token),Map.of("version",1,"reason","停止展示"),200);
        assertThat(data(send(as(get("/api/v1/me"),userToken),null,200)).get("roles").toString()).doesNotContain("teacher");
        assertThat(jdbc.queryForObject("SELECT user_id FROM content_teacher WHERE id=?",String.class,teacherId)).isEqualTo(id);
    }
    @Test void followingIsIdempotentAndScopedToEachUser() throws Exception {
        String token=admin(),id=create(token,input("关注对象","published")).get("id").asText();
        String a=sessions.create(user()).accessToken(),b=sessions.create(user()).accessToken();
        send(as(put("/api/v1/me/follows/"+id),a),Map.of("followed",true),200);
        send(as(put("/api/v1/me/follows/"+id),a),Map.of("followed",true),200);
        assertThat(data(send(as(get("/api/v1/teachers/"+id),a),null,200)).get("followed").asBoolean()).isTrue();
        assertThat(data(send(as(get("/api/v1/teachers"),b),null,200)).get("items").get(0).get("followed").asBoolean()).isFalse();
        send(as(put("/api/v1/me/follows/"+id),a),Map.of("followed",false),200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM teacher_follow",Long.class)).isEqualTo(1);
        send(put("/api/v1/me/follows/"+id),Map.of("followed",true),401);
        send(as(put("/api/v1/me/follows/"+id),token),Map.of("followed",true),403);
    }
    @Test void archivedTeachersKeepHistoryAndRepeatArchiveDoesNotRepeatAudit() throws Exception {
        String token=admin(),id=create(token,input("归档","published")).get("id").asText(),u=sessions.create(user()).accessToken();
        send(as(put("/api/v1/me/follows/"+id),u),Map.of("followed",true),200);
        send(as(delete(BASE+"/"+id),token),Map.of("version",1,"reason","下线"),200);
        send(as(delete(BASE+"/"+id),token),Map.of("version",1,"reason","重试"),200);
        send(get("/api/v1/teachers/"+id),null,404);send(as(put("/api/v1/me/follows/"+id),u),Map.of("followed",true),404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM teacher_follow",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='content.teacher.archive'",Long.class)).isEqualTo(1);
    }
    @Test void paginationAndSearchEscapeWildcardsAndRejectUnsupportedFilters() throws Exception {
        String token=admin();create(token,input("100%讲师","published"));create(token,input("1000讲师","published"));
        assertThat(data(send(get("/api/v1/teachers").param("q","100%"),null,200)).get("total").asInt()).isEqualTo(1);
        assertThat(data(send(get("/api/v1/teachers?page=2&pageSize=1"),null,200)).get("items").size()).isEqualTo(1);
        send(get("/api/v1/teachers?pageSize=101"),null,400);send(get("/api/v1/teachers?page=0"),null,400);
        send(as(get(BASE+"?categoryId=unused"),token),null,400);send(as(get(BASE+"?courseId=unused"),token),null,400);
    }
    @Test void avatarReferencesMustBeReadyImagesAndRespectOwnership() throws Exception {
        String token=admin(),owner=jdbc.queryForObject("SELECT id FROM admin_account WHERE username='integration-admin'",String.class);
        var body=input("头像检查","published");body.put("avatarAssetId","missing");send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),body,400);
        String uploading=avatar(owner,"avatar","uploading");body.put("avatarAssetId",uploading);send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),body,409);
        String cover=avatar(owner,"cover","ready");body.put("avatarAssetId",cover);send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),body,409);
        String otherUser=user(),foreign=avatar(owner,"avatar","ready");
        jdbc.update("UPDATE media_asset SET owner_admin_id=NULL,owner_user_id=? WHERE id=?",otherUser,foreign);
        body.put("avatarAssetId",foreign);send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),body,403);
        String ready=avatar(owner,"avatar","ready");body.put("avatarAssetId",ready);var created=create(token,body);
        assertThatThrownBy(()->jdbc.update("UPDATE media_asset SET purpose='course_media' WHERE id=?",ready))
            .isInstanceOf(org.springframework.dao.DataAccessException.class).hasRootCauseInstanceOf(java.sql.SQLException.class);
        assertThat(jdbc.queryForObject("SELECT purpose FROM media_asset WHERE id=?",String.class,ready)).isEqualTo("avatar");
        assertThat(created.get("avatarUrl").asText()).startsWith("https://assets.example.test/");
        String id=created.get("id").asText();jdbc.update("UPDATE admin_account SET data_scope='own_content',teacher_id=?",id);
        send(as(put(BASE+"/"+id),token),update(body,1),200);
    }
    @Test void permissionChangesAndMalformedJsonAreRejectedWithoutWriting() throws Exception {
        String token=admin();jdbc.update("DELETE FROM admin_role_permission WHERE permission_code='content:write'");
        send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),input("禁止","draft"),403);
        jdbc.update("INSERT INTO admin_role_permission(role_id,permission_code) VALUES ('role-content-manager','content:write')");
        var missing=input("缺失","draft");missing.remove("userId");send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),missing,400);
        var unknown=input("未知","draft");unknown.put("roles",List.of("admin"));send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),unknown,400);
        var empty=input("空资历","draft");empty.put("credentials",List.of(""));send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),empty,400);
        send(as(post(BASE).header("Idempotency-Key","invalid"),token),input("错误键","draft"),400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_teacher",Long.class)).isZero();
    }
    @Test void publicAndAdminResponseShapesMatchCorrectedOpenApi() throws Exception {
        var spec=json.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("contract.path"))));
        String token=admin();var body=input("契约","published");var teacher=create(token,body);
        var adminSchema=spec.get("paths").get("/admin/content/teachers").get("post").at("/responses/200/content/application~1json/schema/properties/data");
        assertFields(teacher,adminSchema);
        var publicTeacher=data(send(get("/api/v1/teachers/"+teacher.get("id").asText()),null,200));
        assertFields(publicTeacher,spec.get("components").get("schemas").get("Teacher"));
        var updateSchema=spec.get("paths").get("/admin/content/teachers/{id}").get("put").at("/requestBody/content/application~1json/schema");
        assertFields(json.valueToTree(update(body,1)),updateSchema);
        Instant.parse(teacher.get("createdAt").asText());assertThat(teacher.get("version").isIntegralNumber()).isTrue();
    }
    private void assertFields(JsonNode actual,JsonNode schema) {
        assertThat(schema.isMissingNode()).isFalse();
        for (var f:schema.get("required")) { assertThat(actual.has(f.asText())).as(f.asText()).isTrue(); }
        actual.fieldNames().forEachRemaining(f->assertThat(schema.get("properties").has(f)).as(f).isTrue());
    }
}
