package com.chengxinjian;

import com.chengxinjian.auth.*;
import com.chengxinjian.common.BusinessException;
import com.fasterxml.jackson.databind.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USERNAME:sa}","spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
    "app.auth.bootstrap-username=integration-admin","app.auth.bootstrap-password=integration-password-12345",
    "app.auth.local-captcha-ticket=integration-captcha-ticket-12345"})
@AutoConfigureMockMvc @ActiveProfiles("local")
class AdminSecurityIntegrationTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;@Autowired PlatformTransactionManager manager;
    String token,actor,role;
    String id(){return UUID.randomUUID().toString();}
    JsonNode http(MockHttpServletRequestBuilder req,String token,Object body,int expected)throws Exception {
        if(token!=null)req.header("Authorization","Bearer "+token);
        if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return json.readTree(mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray()).get("data");
    }
    String login(String username,String password,int expected)throws Exception {
        var response=http(post("/api/v1/admin/auth/login"),null,Map.of("username",username,"password",password,"captchaTicket","integration-captcha-ticket-12345"),expected);
        return expected==200?response.get("accessToken").asText():null;
    }
    @BeforeEach void setup()throws Exception {
        role=id();actor=jdbc.queryForObject("SELECT id FROM admin_account WHERE username='integration-admin'",String.class);
        jdbc.update("UPDATE admin_account SET enabled=TRUE,data_scope='all',password_hash=? WHERE id=?",passwords.encode("integration-password-12345"),actor);
        jdbc.update("DELETE FROM login_throttle");
        jdbc.update("INSERT INTO admin_role(id,name) VALUES (?,?)",role,"安全测试"+role);
        jdbc.update("INSERT INTO admin_role_permission(role_id,permission_code) SELECT ?,code FROM permission",role);
        jdbc.update("INSERT INTO admin_account_role VALUES (?,?)",actor,role);
        token=login("integration-admin","integration-password-12345",200);
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM auth_session WHERE account_id IN (SELECT id FROM admin_account WHERE username LIKE 'security-%')");
        jdbc.update("DELETE FROM admin_account_role WHERE account_id IN (SELECT id FROM admin_account WHERE username LIKE 'security-%')");
        jdbc.update("DELETE FROM admin_account WHERE username LIKE 'security-%'");
        jdbc.update("DELETE FROM admin_account_role WHERE role_id=?",role);
        jdbc.update("DELETE FROM admin_role_permission WHERE role_id=?",role);jdbc.update("DELETE FROM admin_role WHERE id=?",role);
        jdbc.update("UPDATE admin_account SET enabled=TRUE,data_scope='all' WHERE id=?",actor);
    }
    Map<String,Object> account(String username,List<String> roles) {
        var body=new HashMap<String,Object>();body.put("username",username);body.put("displayName","后台用户");body.put("roleIds",roles);body.put("teacherId",null);body.put("enabled",true);body.put("initialPassword","security-password-12345");return body;
    }
    JsonNode createAccount(String username,List<String> roles)throws Exception{return http(post("/api/v1/admin/accounts").header("Idempotency-Key",id()),token,account(username,roles),200);}
    @Test void accountCreationIsIdempotentAndPasswordResetRevokesSessions()throws Exception {
        String username="security-"+id(),key=id();var body=account(username,List.of("role-content-manager"));
        var first=http(post("/api/v1/admin/accounts").header("Idempotency-Key",key),token,body,200);
        assertThat(http(post("/api/v1/admin/accounts").header("Idempotency-Key",key),token,body,200)).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT request_hash FROM idempotency_record WHERE actor_id=? AND idempotency_key=?",String.class,actor,key)).startsWith("$2");
        assertThat(first.toString()).doesNotContain("password","hash");String other=login(username,"security-password-12345",200);
        http(get("/api/v1/admin/roles"),other,null,403);
        String target=first.get("id").asText(),resetKey=id();
        var reset=Map.of("newPassword","changed-password-12345","reason","管理员协助重置");
        http(post("/api/v1/admin/accounts/"+target+"/reset-password").header("Idempotency-Key",resetKey),token,reset,200);
        http(get("/api/v1/admin/me"),other,null,401);login(username,"security-password-12345",401);
        String fresh=login(username,"changed-password-12345",200);
        http(post("/api/v1/admin/accounts/"+target+"/reset-password").header("Idempotency-Key",resetKey),token,reset,200);
        http(get("/api/v1/admin/me"),fresh,null,200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE target_id=? AND action='admin.account.password'",Long.class,target)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT result_json FROM idempotency_record WHERE actor_id=? AND idempotency_key=?",String.class,actor,key)).doesNotContain("security-password");
    }
    @Test void roleDictionaryScopeAndLastAdministratorAreEnforced()throws Exception {
        http(post("/api/v1/admin/roles").header("Idempotency-Key",id()),token,Map.of("name","非法权限","permissions",List.of("finance:imaginary"),"dataScope","all"),400);
        http(post("/api/v1/admin/roles").header("Idempotency-Key",id()),token,Map.of("name","越权范围","permissions",List.of("rbac:write"),"dataScope","own_content"),400);
        var own=http(post("/api/v1/admin/roles").header("Idempotency-Key",id()),token,Map.of("name","本人内容"+id(),"permissions",List.of("content:read"),"dataScope","own_content"),200);
        String ownId=own.get("id").asText();
        try {
            http(post("/api/v1/admin/accounts").header("Idempotency-Key",id()),token,account("security-"+id(),List.of(ownId)),400);
            http(delete("/api/v1/admin/roles/"+role),token,null,409);
            http(put("/api/v1/admin/roles/"+role),token,Map.of("name","不能清空最后管理员","permissions",List.of(),"dataScope","all","version",1),409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_role_permission WHERE role_id=? AND permission_code='rbac:write'",Long.class,role)).isEqualTo(1);
        } finally {http(delete("/api/v1/admin/roles/"+ownId),token,null,200);}
    }
    @Test void concurrentDisableCannotRemoveBothGlobalAdministrators()throws Exception {
        var second=createAccount("security-"+id(),List.of(role));String secondId=second.get("id").asText();
        String other=login(second.get("username").asText(),"security-password-12345",200);
        var first=http(get("/api/v1/admin/accounts"),token,null,200).get("items");JsonNode self=null;
        for(var a:first)if(actor.equals(a.get("id").asText()))self=a;
        final JsonNode selfNode=self;
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks=new ArrayList<java.util.concurrent.Callable<Integer>>();
            for(var pair:List.of(Map.entry(selfNode,token),Map.entry(second,other)))tasks.add(()->{
                var b=pair.getKey().deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)b).remove(List.of("id","createdAt","updatedAt"));((com.fasterxml.jackson.databind.node.ObjectNode)b).put("enabled",false);
                return mvc.perform(put("/api/v1/admin/accounts/"+pair.getKey().get("id").asText()).header("Authorization","Bearer "+pair.getValue()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(b))).andReturn().getResponse().getStatus();
            });
            var statuses=new ArrayList<Integer>();for(var f:pool.invokeAll(tasks))statuses.add(f.get());assertThat(statuses).containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account WHERE id IN (?,?) AND enabled=TRUE",Long.class,actor,secondId)).isEqualTo(1);
    }
    @Test void secureBootstrapRefusesExistingAccountsWithoutMutatingThem() {
        var before=jdbc.queryForObject("SELECT COUNT(*) FROM admin_account",Long.class);
        var tx=new TransactionTemplate(manager);
        assertThatThrownBy(()->tx.executeWithoutResult(s->AdminInitialization.initialize(jdbc,passwords,Clock.systemUTC(),"new-owner","负责人","strong-password-12345","初始化演练")))
            .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account",Long.class)).isEqualTo(before);
    }

    @Test void roleEditsImmediatelyAffectExistingSessionsAndAuditIsPermissionProtected()throws Exception {
        String today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        var limited=http(post("/api/v1/admin/roles").header("Idempotency-Key",id()),token,Map.of("name","只读角色"+id(),"permissions",List.of("content:read"),"dataScope","all"),200);
        String roleId=limited.get("id").asText();
        var created=createAccount("security-"+id(),List.of(roleId));String username=created.get("username").asText();
        String reader=login(username,"security-password-12345",200);
        http(get("/api/v1/admin/content/categories"),reader,null,200);
        var update=Map.of("name",limited.get("name").asText(),"permissions",List.of(),"dataScope","all","version",1);
        http(put("/api/v1/admin/roles/"+roleId),token,update,200);
        http(get("/api/v1/admin/content/categories"),reader,null,403);
        http(put("/api/v1/admin/roles/"+roleId),token,update,409);
        http(get("/api/v1/admin/audit-logs").param("from",today).param("to",today),reader,null,403);
        var logs=http(get("/api/v1/admin/audit-logs").param("from",today).param("to",today).param("operatorId",actor).param("action","admin.role.update"),token,null,200);
        assertThat(logs.get("total").asInt()).isPositive();
        var body=account(username,List.of(roleId));http(post("/api/v1/admin/accounts").header("Idempotency-Key",id()),token,body,409);
        jdbc.update("DELETE FROM auth_session WHERE account_id=?",created.get("id").asText());
        jdbc.update("DELETE FROM admin_account_role WHERE account_id=?",created.get("id").asText());
        jdbc.update("DELETE FROM admin_account WHERE id=?",created.get("id").asText());
        http(delete("/api/v1/admin/roles/"+roleId),token,null,200);
    }
}
