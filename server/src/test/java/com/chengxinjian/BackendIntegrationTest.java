package com.chengxinjian;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.StreamSupport;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USERNAME:sa}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
    "app.auth.bootstrap-username=integration-admin", "app.auth.bootstrap-password=integration-password-12345",
    "app.auth.local-captcha-ticket=integration-captcha-ticket-12345"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class BackendIntegrationTest {
    private static final String BASE = "/api/v1/admin/content/categories";
    private static final String CAPTCHA = "integration-captcha-ticket-12345";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void reset() {
        jdbc.update("DELETE FROM idempotency_record");
        jdbc.update("DELETE FROM content_category");
        jdbc.update("DELETE FROM auth_session");
        jdbc.update("DELETE FROM login_throttle");
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("UPDATE admin_account SET enabled=TRUE,data_scope='all'");
        jdbc.update("DELETE FROM admin_role_permission WHERE role_id='role-content-manager'");
        jdbc.update("INSERT INTO admin_role_permission(role_id,permission_code) VALUES ('role-content-manager','content:read')");
        jdbc.update("INSERT INTO admin_role_permission(role_id,permission_code) VALUES ('role-content-manager','content:write')");
    }
    private MvcResult send(MockHttpServletRequestBuilder request, Object body, int status) throws Exception {
        if (body != null) { request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)); }
        return mvc.perform(request).andExpect(status().is(status)).andReturn();
    }
    private JsonNode data(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()).get("data"); }
    private JsonNode login() throws Exception {
        return data(send(post("/api/v1/admin/auth/login"), Map.of("username","integration-admin", "password","integration-password-12345", "captchaTicket",CAPTCHA), 200));
    }
    private String token() throws Exception { return login().get("accessToken").asText(); }
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) { return request.header("Authorization", "Bearer " + token); }
    private Map<String,Object> category(String name, String state) { return Map.of("name",name,"kind","course","sort",1,"status",state); }
    private JsonNode create(String token, String name, String status) throws Exception {
        return data(send(as(post(BASE).header("Idempotency-Key", UUID.randomUUID().toString()), token), category(name,status), 200));
    }
    @Test void healthDoesNotExposeDatabaseDetails() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
    }
    @Test void tokensAreHashedAndRequestIdsAreCorrelated() throws Exception {
        var result = send(post("/api/v1/admin/auth/login"), Map.of("username","integration-admin","password","integration-password-12345","captchaTicket",CAPTCHA),200);
        var root = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(root.get("requestId").asText()).isEqualTo(result.getResponse().getHeader("X-Request-ID"));
        assertThat(root.get("data").has("admin")).isTrue();
        assertThat(root.get("data").get("admin").has("passwordHash")).isFalse();
        var row = jdbc.queryForMap("SELECT access_hash,refresh_hash FROM auth_session");
        assertThat(row.get("access_hash").toString()).hasSize(64).isNotEqualTo(root.at("/data/accessToken").asText());
        assertThat(jdbc.queryForObject("SELECT password_hash FROM admin_account WHERE username='integration-admin'", String.class)).startsWith("$2").doesNotContain("integration-password");
    }
    @Test void anonymousCannotReadOrWriteAdminResources() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        send(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()), category("不允许","draft"),401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_category",Long.class)).isZero();
    }
    @Test void publishedCatalogHidesDraftsAndArchiveIsRepeatable() throws Exception {
        String token = token();
        JsonNode draft = create(token,"草稿分类","draft");
        JsonNode published = create(token,"已发布分类","published");
        var publicItems = data(send(get("/api/v1/categories?kind=course"),null,200));
        assertThat(publicItems.size()).isEqualTo(1);
        assertThat(publicItems.get(0).get("id").asText()).isEqualTo(published.get("id").asText());
        String id = draft.get("id").asText();
        var update = Map.of("name","发布后的分类","kind","course","sort",2,"status","published","version",1);
        assertThat(data(send(as(put(BASE+"/"+id),token),update,200)).get("version").asInt()).isEqualTo(2);
        assertThat(data(send(get("/api/v1/categories?kind=course"),null,200)).size()).isEqualTo(2);
        send(as(delete(BASE+"/"+id),token),Map.of("version",2,"reason","停用此分类"),200);
        send(as(delete(BASE+"/"+id),token),Map.of("version",2,"reason","停用此分类"),200);
        assertThat(data(send(get("/api/v1/categories?kind=course"),null,200)).size()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_category",Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='content.category.archive'",Long.class)).isEqualTo(1);
    }
    @Test void staleUpdatesDoNotOverwriteData() throws Exception {
        String token = token();
        String id = create(token,"原始","draft").get("id").asText();
        var input = Map.of("name","第一次修改","kind","course","sort",1,"status","published","version",1);
        send(as(put(BASE+"/"+id),token),input,200);
        var conflict = send(as(put(BASE+"/"+id),token),input,409);
        assertThat(json.readTree(conflict.getResponse().getContentAsByteArray()).get("code").asText()).isEqualTo("VERSION_CONFLICT");
    }
    @Test void createRetryReplaysOriginalResultEvenAfterEditing() throws Exception {
        String token = token(); String key = UUID.randomUUID().toString(); var input = category("原始分类","draft");
        JsonNode original = data(send(as(post(BASE).header("Idempotency-Key",key),token),input,200));
        String id = original.get("id").asText();
        send(as(put(BASE+"/"+id),token),Map.of("name","已修改","kind","course","sort",1,"status","published","version",1),200);
        JsonNode replay = data(send(as(post(BASE).header("Idempotency-Key",key),token),input,200));
        assertThat(replay).isEqualTo(original);
        var conflict = send(as(post(BASE).header("Idempotency-Key",key),token),category("另一份请求","draft"),409);
        assertThat(json.readTree(conflict.getResponse().getContentAsByteArray()).get("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_category",Long.class)).isEqualTo(1);
    }
    @Test void concurrentRetriesCreateOneCategory() throws Exception {
        String token = token(); String key = UUID.randomUUID().toString();
        Callable<String> create = () -> data(send(as(post(BASE).header("Idempotency-Key",key),token),category("并发分类","draft"),200)).get("id").asText();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(create); var b = pool.submit(create);
            assertThat(a.get()).isEqualTo(b.get());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_category",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='content.category.create'",Long.class)).isEqualTo(1);
    }
    @Test void unknownFieldsMissingFieldsAndInvalidEnumsAreRejected() throws Exception {
        String token = token();
        send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),Map.of("name","分类","kind","course","sort",1,"status","draft","paid",true),400);
        send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),Map.of("name","分类","kind","course","status","draft"),400);
        send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),category("  ","draft"),400);
        send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),category("分类","unknown"),400);
        send(as(post(BASE),token),category("分类","draft"),400);
        send(as(post(BASE).header("Idempotency-Key","invalid"),token),category("分类","draft"),400);
        send(get("/api/v1/categories?kind=invalid"),null,400);
        send(as(get(BASE+"?page=0"),token),null,400);
        send(as(get(BASE+"?pageSize=101"),token),null,400);
    }
    @Test void listPaginationAndLiteralSearchWork() throws Exception {
        String token = token();
        create(token,"含%符号","draft"); create(token,"普通分类","published");
        var page = data(send(as(get(BASE+"?page=1&pageSize=1"),token),null,200));
        assertThat(page.get("total").asInt()).isEqualTo(2); assertThat(page.get("items").size()).isEqualTo(1);
        var filtered = data(send(as(get(BASE).param("q","%"),token),null,200));
        assertThat(filtered.get("total").asInt()).isEqualTo(1);
        assertThat(data(send(as(get(BASE+"?page=999"),token),null,200)).get("items").isEmpty()).isTrue();
    }
    @Test void ambiguousJsonAndImplicitTypeCoercionAreRejected() throws Exception {
        String token = token();
        for (String body : java.util.List.of(
            "{\"name\":\"分类\",\"name\":\"覆盖\",\"kind\":\"course\",\"sort\":1,\"status\":\"draft\"}",
            "{\"name\":\"分类\",\"kind\":\"course\",\"sort\":1.9,\"status\":\"draft\"}",
            "{\"name\":\"分类\",\"kind\":\"course\",\"sort\":\"1\",\"status\":\"draft\"}",
            "{\"name\":\"分类\",\"kind\":\"course\",\"sort\":1,\"status\":\"draft\"} {}")) {
            mvc.perform(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_category",Long.class)).isZero();
    }
    @Test void refreshRotatesBothTokensAndOldCredentialsCannotBeReused() throws Exception {
        var initial = login();
        var next = data(send(post("/api/v1/admin/auth/refresh"),Map.of("refreshToken",initial.get("refreshToken").asText()),200));
        send(as(get("/api/v1/admin/me"),initial.get("accessToken").asText()),null,401);
        send(post("/api/v1/admin/auth/refresh"),Map.of("refreshToken",initial.get("refreshToken").asText()),401);
        send(as(get("/api/v1/admin/me"),next.get("accessToken").asText()),null,200);
    }
    @Test void concurrentRefreshHasExactlyOneWinner() throws Exception {
        String refresh = login().get("refreshToken").asText();
        Callable<Integer> call = () -> mvc.perform(post("/api/v1/admin/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("refreshToken",refresh)))).andReturn().getResponse().getStatus();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(call); var b = pool.submit(call);
            assertThat(java.util.List.of(a.get(),b.get())).containsExactlyInAnyOrder(200,401);
        }
    }
    @Test void logoutRevokesSessionAndRejectsFurtherAccess() throws Exception {
        var initial = login(); String access = initial.get("accessToken").asText();
        send(as(post("/api/v1/admin/auth/logout"),access),Map.of("refreshToken",initial.get("refreshToken").asText()),200);
        send(as(get("/api/v1/admin/me"),access),null,401);
        send(post("/api/v1/admin/auth/refresh"),Map.of("refreshToken",initial.get("refreshToken").asText()),401);
    }
    @Test void disablingAccountAndChangingPermissionsTakeEffectImmediately() throws Exception {
        String token = token();
        jdbc.update("DELETE FROM admin_role_permission WHERE permission_code='content:write'");
        send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),category("禁止写入","draft"),403);
        send(as(get(BASE),token),null,200);
        jdbc.update("UPDATE admin_account SET enabled=FALSE");
        send(as(get("/api/v1/admin/me"),token),null,401);
    }
    @Test void teacherScopeCannotManageGlobalCategories() throws Exception {
        String token = token(); jdbc.update("UPDATE admin_account SET data_scope='own_content'");
        send(as(get(BASE),token),null,403);
        send(as(post(BASE).header("Idempotency-Key",UUID.randomUUID().toString()),token),category("越权写入","draft"),403);
    }
    @Test void wrongAudienceAndExpiredTokensAreRejected() throws Exception {
        var credentials = login();
        String token = credentials.get("accessToken").asText(); jdbc.update("UPDATE auth_session SET audience='user'");
        send(as(get("/api/v1/admin/me"),token),null,401);
        jdbc.update("UPDATE auth_session SET audience='admin',access_expires_at=?",Timestamp.from(Instant.now().minusSeconds(60)));
        var result = send(as(get("/api/v1/admin/me"),token),null,401);
        assertThat(json.readTree(result.getResponse().getContentAsByteArray()).get("code").asText()).isEqualTo("TOKEN_EXPIRED");
        send(as(get("/api/v1/categories?kind=course"),token),null,401);
        send(as(post("/api/v1/admin/auth/refresh"),token),Map.of("refreshToken",credentials.get("refreshToken").asText()),200);
    }
    @Test void adminTokenCannotBecomeAUserToken() throws Exception {
        send(as(get("/api/v1/categories?kind=course"),token()),null,403);
    }
    @Test void badPasswordAndCaptchaDoNotCreateSessionsAndLoginIsThrottled() throws Exception {
        var body = Map.of("username","integration-admin","password","incorrect-password-123","captchaTicket",CAPTCHA);
        for (int i=0;i<10;i++) { send(post("/api/v1/admin/auth/login"),body,401); }
        var limited = send(post("/api/v1/admin/auth/login"),body,429);
        assertThat(limited.getResponse().getHeader("Retry-After")).isEqualTo("60");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_session",Long.class)).isZero();
        jdbc.update("DELETE FROM login_throttle");
        send(post("/api/v1/admin/auth/login"),Map.of("username","integration-admin","password","integration-password-12345","captchaTicket","forged"),400);
    }
    @Test void responseFieldsMatchExistingContractForImplementedOperations() throws Exception {
        JsonNode spec = json.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("contract.path"))));
        JsonNode admin = login(); String token = admin.get("accessToken").asText();
        assertRequiredFields(admin, spec.at("/components/schemas/AdminAuth"));
        assertRequiredFields(admin.get("admin"),spec.at("/components/schemas/AdminProfile"));
        JsonNode created = create(token,"契约分类","published");
        assertRequiredFields(created,spec.at("/components/schemas/Category"));
        assertThat(created.get("id").isTextual()).isTrue();
        assertThat(created.get("createdAt").isTextual()).isTrue();
        Instant.parse(created.get("createdAt").asText());
        for (var operation : java.util.List.of("post /admin/auth/login","post /admin/auth/refresh","post /admin/auth/logout","get /admin/me", "get /categories","get /admin/content/categories","post /admin/content/categories","get /admin/content/categories/{id}","put /admin/content/categories/{id}","delete /admin/content/categories/{id}")) {
            var pair = operation.split(" ",2);
            assertThat(spec.get("paths").get(pair[1]).has(pair[0])).as(operation).isTrue();
        }
    }
    private void assertRequiredFields(JsonNode actual, JsonNode schema) {
        assertThat(schema.isMissingNode()).isFalse();
        for (JsonNode required : schema.get("required")) { assertThat(actual.has(required.asText())).as(required.asText()).isTrue(); }
        var fields = StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(actual.fieldNames(),0),false).toList();
        for (String field : fields) { assertThat(schema.get("properties").has(field)).as("Unexpected response field: " + field).isTrue(); }
    }
}
