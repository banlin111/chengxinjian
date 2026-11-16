package com.chengxinjian;

import com.chengxinjian.common.BusinessException;
import com.chengxinjian.user.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:users;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USERNAME:sa}","spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
    "app.auth.bootstrap-username=integration-admin","app.auth.bootstrap-password=integration-password-12345",
    "app.auth.local-captcha-ticket=integration-captcha-ticket-12345"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class UserIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean WechatGateway wechat;
    @MockitoBean WechatPhoneGateway phoneGateway;
    @MockitoBean PhoneProviders smsProvider;
    @BeforeEach void reset() {
        CourseFixtures.clear(jdbc);
        jdbc.update("DELETE FROM phone_reauth");
        jdbc.update("DELETE FROM phone_sms_challenge");
        jdbc.update("UPDATE admin_account SET teacher_id=NULL");
        for (var table:List.of("media_processing_job","media_upload_request","teacher_follow","content_teacher","media_asset","user_agreement_consent","user_auth_session","wechat_identity","user_settings","app_user","wechat_code_use","login_throttle","audit_log","auth_session")) {
            jdbc.update("DELETE FROM "+table);
        }
        jdbc.update("DELETE FROM agreement_version WHERE test_only=FALSE");
        jdbc.update("UPDATE agreement_version SET published=TRUE WHERE test_only=TRUE");
        jdbc.update("UPDATE admin_account SET enabled=TRUE");
        when(wechat.exchange(anyString())).thenReturn(new WechatGateway.Identity("wx0123456789abcdef","open-one",null));
    }
    private MvcResult send(MockHttpServletRequestBuilder request,Object body,int status) throws Exception {
        if (body!=null) { request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)); }
        return mvc.perform(request).andExpect(status().is(status)).andReturn();
    }
    private JsonNode data(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()).get("data"); }
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r,String token) { return r.header("Authorization","Bearer "+token); }
    private List<Map<String,String>> refs() { return List.of(Map.of("type","privacy","version","local-test-2026-11-09"),Map.of("type","user","version","local-test-2026-11-09")); }
    private JsonNode login(String code) throws Exception { return data(send(post("/api/v1/auth/wechat/login"),Map.of("code",code,"agreements",refs()),200)); }
    private String access(JsonNode n) { return n.get("accessToken").asText(); }
    private String refresh(JsonNode n) { return n.get("refreshToken").asText(); }
    private JsonNode otherLogin(String code) throws Exception {
        when(wechat.exchange(code)).thenReturn(new WechatGateway.Identity("wx0123456789abcdef","open-two",null));
        return login(code);
    }
    private String reauth(JsonNode login,String code) throws Exception {
        return data(send(as(post("/api/v1/auth/reauth/wechat"),access(login)),
            Map.of("code",code,"purpose","change_phone"),200)).path("reauthToken").asText();
    }
    private Map<String,String> sms(JsonNode user,String phone,String purpose) throws Exception {
        var captor=org.mockito.ArgumentCaptor.forClass(String.class);
        var result=data(send(as(post("/api/v1/auth/sms"),access(user)),
            Map.of("phone",phone,"purpose",purpose,"captchaTicket","human-ticket"),200));
        verify(smsProvider,atLeastOnce()).send(eq(phone),captor.capture());
        assertThat(result.has("code")).isFalse();
        return Map.of("challengeId",result.path("challengeId").asText(),"code",captor.getValue());
    }
    private JsonNode wxBind(JsonNode user,String code,String number,String reauth,int status) throws Exception {
        when(phoneGateway.exchange(code)).thenReturn(number);
        var body=new HashMap<String,String>(); body.put("phoneCode",code);
        if (reauth!=null) body.put("reauthToken",reauth);
        var result=send(as(put("/api/v1/me/phone/wechat"),access(user)),body,status);
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
    private void seedMembership(JsonNode user,String source) {
        jdbc.update("INSERT INTO entitlement_grant(id,user_id,kind,target_id,source,source_id,starts_at) VALUES (?,?,'membership',?,'admin',?,?)",
            UUID.randomUUID().toString(),user.at("/user/id").asText(),source,source,Timestamp.from(Instant.now().minusSeconds(1)));
    }
    @Test void smsConflictPreservesBothOwnersSessionsAndIndependentGrants() throws Exception {
        var a=login("a"); var b=otherLogin("b");
        String aId=a.at("/user/id").asText(), bId=b.at("/user/id").asText();
        seedMembership(a,"gift-a"); seedMembership(b,"gift-b");
        wxBind(a,"bind-a","13800000001",null,200); wxBind(b,"bind-b","13800000002",null,200);
        var originalOwners=jdbc.queryForList("SELECT id,user_id,target_id,source_id,starts_at,expires_at,revoked_at FROM entitlement_grant ORDER BY id");
        var proof=new HashMap<>(sms(a,"13800000002","change_phone"));
        proof.put("reauthToken",reauth(a,"reauth-a"));
        var conflict=send(as(put("/api/v1/me/phone"),access(a)),proof,409);
        String error=conflict.getResponse().getContentAsString();
        assertThat(error).contains("PHONE_ALREADY_BOUND").doesNotContain("13800000002",bId);
        send(as(put("/api/v1/me/phone"),access(a)),proof,403);
        assertThat(jdbc.queryForList("SELECT id,user_id,target_id,source_id,starts_at,expires_at,revoked_at FROM entitlement_grant ORDER BY id")).isEqualTo(originalOwners);
        for (var entry:List.of(a,b)) {
            var profile=data(send(as(get("/api/v1/me"),access(entry)),null,200));
            assertThat(profile.path("id")).isEqualTo(entry.at("/user/id"));
            assertThat(profile.path("phoneMasked").asText()).isEqualTo(entry==a?"138****0001":"138****0002");
            assertThat(profile.at("/membership/planId").asText()).isEqualTo(entry==a?"gift-a":"gift-b");
            send(post("/api/v1/auth/refresh"),Map.of("refreshToken",refresh(entry)),200);
        }
        assertThat(login("a-again").at("/user/id").asText()).isEqualTo(aId);
        assertThat(login("a-new-login").path("phoneBindingRequired").asBoolean()).isFalse();
        assertThat(otherLogin("b-again").at("/user/id").asText()).isEqualTo(bId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class)).isEqualTo(2);
    }
    @Test void concurrentVerifiedClaimsHaveExactlyOneOwnerAndLoserKeepsOriginalNumber() throws Exception {
        var a=login("a"); var b=otherLogin("b");
        wxBind(a,"old-a","13800000001",null,200); wxBind(b,"old-b","13800000002",null,200);
        when(wechat.exchange("proof-b")).thenReturn(new WechatGateway.Identity("wx0123456789abcdef","open-two",null));
        String pa=reauth(a,"proof-a"),pb=reauth(b,"proof-b");
        when(phoneGateway.exchange("target-a")).thenReturn("13800000003");
        when(phoneGateway.exchange("target-b")).thenReturn("13800000003");
        var ready=new java.util.concurrent.CountDownLatch(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var calls=new ArrayList<java.util.concurrent.Future<MvcResult>>();
            for(var u:List.of(a,b)) calls.add(pool.submit(()->{
                ready.countDown();start.await();
                return mvc.perform(as(put("/api/v1/me/phone/wechat"),access(u)).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("phoneCode",u==a?"target-a":"target-b","reauthToken",u==a?pa:pb)))).andReturn();
            }));
            assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
            var x=calls.get(0).get().getResponse();var y=calls.get(1).get().getResponse();
            assertThat(List.of(x.getStatus(),y.getStatus())).containsExactlyInAnyOrder(200,409);
            assertThat((x.getStatus()==409?x:y).getContentAsString()).contains("PHONE_ALREADY_BOUND");
            assertThat(jdbc.queryForObject("SELECT phone FROM app_user WHERE id=?",String.class,a.at("/user/id").asText()))
                .isEqualTo(x.getStatus()==200?"13800000003":"13800000001");
            assertThat(jdbc.queryForObject("SELECT phone FROM app_user WHERE id=?",String.class,b.at("/user/id").asText()))
                .isEqualTo(y.getStatus()==200?"13800000003":"13800000002");
        }
    }
    @Test void bindingProofsAreBoundToSessionPurposeVersionExpiryAndFiveAttempts() throws Exception {
        var a=login("wx-a"); var second=login("second-session");
        var proof=new HashMap<>(sms(a,"13800000001","change_phone")); String token=reauth(a,"fresh-wx");
        proof.put("reauthToken",token);
        send(as(put("/api/v1/me/phone"),access(second)),proof,403);
        var reauthBody=new HashMap<>(proof);reauthBody.remove("reauthToken");reauthBody.put("purpose","change_phone");
        send(as(post("/api/v1/auth/reauth"),access(a)),reauthBody,400);
        var wrong=new HashMap<>(proof);wrong.put("code",proof.get("code").equals("111111")?"222222":"111111");
        for(int i=0;i<5;i++) send(as(put("/api/v1/me/phone"),access(a)),wrong,400);
        send(as(put("/api/v1/me/phone"),access(a)),proof,400);
        var fresh=new HashMap<>(sms(a,"13800000003","change_phone"));fresh.put("reauthToken",token);
        var bound=data(send(as(put("/api/v1/me/phone"),access(a)),fresh,200));
        assertThat(bound.path("phoneMasked").asText()).isEqualTo("138****0003");
        wxBind(a,"needs-proof","13800000004",null,403);
        verify(phoneGateway,never()).exchange("needs-proof");
        var expired=reauth(a,"exp");jdbc.update("UPDATE phone_reauth SET expires_at=?",Timestamp.from(Instant.now().minusSeconds(3)));
        wxBind(a,"expired","13800000004",expired,403);
        when(wechat.exchange("other-identity")).thenReturn(new WechatGateway.Identity("wx0123456789abcdef","not-the-owner",null));
        send(as(post("/api/v1/auth/reauth/wechat"),access(a)),Map.of("purpose","change_phone","code","other-identity"),403);
        String stale=reauth(a,"old-version"),valid=reauth(a,"new-version");
        wxBind(a,"verified-new","13800000004",valid,200);
        wxBind(a,"stale","13800000005",stale,403);
    }
    @Test void oldPhoneSmsMustMatchCurrentOwnerAndNewPhoneMustHaveItsOwnProof() throws Exception {
        var a=login("current");wxBind(a,"old","13800000001",null,200);
        send(as(post("/api/v1/auth/sms"),access(a)),Map.of("phone","13800000009","purpose","reauth","captchaTicket","ticket"),403);
        verify(smsProvider,never()).send(eq("13800000009"),anyString());
        var old=new HashMap<>(sms(a,"13800000001","reauth"));old.put("purpose","change_phone");
        var proof=data(send(as(post("/api/v1/auth/reauth"),access(a)),old,200)).path("reauthToken").asText();
        send(as(post("/api/v1/auth/reauth"),access(a)),old,400);
        var fresh=new HashMap<>(sms(a,"13800000002","change_phone"));fresh.put("reauthToken",proof);
        send(as(post("/api/v1/auth/sms"),access(a)),Map.of("phone","13800000002","purpose","change_phone","captchaTicket","ticket"),429);
        var changed=data(send(as(put("/api/v1/me/phone"),access(a)),fresh,200));
        assertThat(changed.path("id")).isEqualTo(a.at("/user/id"));
        assertThat(changed.path("phoneMasked").asText()).isEqualTo("138****0002");
        send(as(put("/api/v1/me/phone"),access(a)),fresh,403);
    }
    @Test void directoryRequiresExplicitGlobalPermissionAndBanCannotResurrectSessions() throws Exception {
        var a=login("member"); var other=otherLogin("nonmember");
        String id=a.at("/user/id").asText(),path="/api/v1/admin/users";
        seedMembership(a,"lifetime");wxBind(a,"masked","13800000001",null,200);
        var admin=data(send(post("/api/v1/admin/auth/login"),Map.of("username","integration-admin","password","integration-password-12345",
            "captchaTicket","integration-captcha-ticket-12345"),200));
        String token=access(admin);
        jdbc.update("UPDATE admin_account SET data_scope='all' WHERE username='integration-admin'");
        jdbc.update("INSERT INTO admin_account_role(account_id,role_id) SELECT id,'role-content-manager' FROM admin_account a WHERE username='integration-admin' AND NOT EXISTS(SELECT 1 FROM admin_account_role r WHERE r.account_id=a.id AND r.role_id='role-content-manager')");
        jdbc.update("DELETE FROM admin_role_permission WHERE role_id='role-content-manager' AND permission_code LIKE 'user_directory:%'");
        send(as(get(path),token),null,403);
        send(as(get(path),access(other)),null,401);
        try {
            for(String perm:List.of("user_directory:read","user_directory:write"))
                jdbc.update("INSERT INTO admin_role_permission(role_id,permission_code) VALUES ('role-content-manager',?)",perm);
            var result=data(send(as(get(path).param("phone","13800000001").param("membershipActive","true"),token),null,200));
            assertThat(result.path("total").asInt()).isEqualTo(1);
            var row=result.at("/items/0"); assertThat(row.path("phoneMasked").asText()).isEqualTo("138****0001");
            assertThat(result.toString()).doesNotContain("13800000001","open-one");
            var v=row.path("version").asInt();
            send(as(put(path+"/"+id+"/roles"),token),Map.of("roles",List.of("user","admin"),"reason","test"),400);
            var role=data(send(as(put(path+"/"+id+"/roles"),token),Map.of("roles",List.of("user","teacher"),"reason","test","version",v),200));
            assertThat(role.path("roles").size()).isEqualTo(2);
            send(as(put(path+"/"+id+"/status"),token),Map.of("status","banned","version",v,"reason","stale"),409);
            var banned=data(send(as(put(path+"/"+id+"/status"),token),
                Map.of("status","banned","version",role.path("version").asInt(),"reason","moderation"),200));
            assertThat(data(send(as(get(path+"/"+id),token),null,200)).path("status").asText()).isEqualTo("banned");
            send(as(get("/api/v1/me"),access(a)),null,401);
            send(as(put(path+"/"+id+"/status"),token),Map.of("status","active","version",banned.path("version").asInt(),"reason","appeal"),200);
            send(post("/api/v1/auth/refresh"),Map.of("refreshToken",refresh(a)),401);
            assertThat(login("after-unban").at("/user/membership/planId").asText()).isEqualTo("lifetime");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE target_id=? AND action LIKE 'user.status.%'",Long.class,id)).isEqualTo(2);
            jdbc.update("UPDATE admin_account SET data_scope='own_content' WHERE username='integration-admin'");
            send(as(get(path),token),null,403);
        } finally {
            jdbc.update("UPDATE admin_account SET data_scope='all' WHERE username='integration-admin'");
            jdbc.update("DELETE FROM admin_role_permission WHERE role_id='role-content-manager' AND permission_code LIKE 'user_directory:%'");
        }
    }
    @Test void providerFailureNeverActivatesChallengeAndExpiredSmsCannotBind() throws Exception {
        var a=login("unavailable");
        doThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"PROVIDER_UNAVAILABLE","unavailable"))
            .when(smsProvider).send(eq("13800000001"),anyString());
        send(as(post("/api/v1/auth/sms"),access(a)),Map.of("phone","13800000001","purpose","change_phone","captchaTicket","ticket"),503);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM phone_sms_challenge WHERE state='sent'",Integer.class)).isZero();
        var b=new HashMap<>(sms(a,"13800000002","change_phone")); b.put("reauthToken",reauth(a,"wx-again"));
        jdbc.update("UPDATE phone_sms_challenge SET expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(1)),b.get("challengeId"));
        send(as(put("/api/v1/me/phone"),access(a)),b,400);
        assertThat(data(send(as(get("/api/v1/me"),access(a)),null,200)).get("phoneMasked").isNull()).isTrue();
    }
    @Test void firstAndRepeatLoginPersistOneUserAndNeverExposeWechatSecrets() throws Exception {
        var first=login("first"); var second=login("second");
        assertThat(first.get("isNewUser").asBoolean()).isTrue(); assertThat(second.get("isNewUser").asBoolean()).isFalse();
        assertThat(first.at("/user/id")).isEqualTo(second.at("/user/id"));
        assertThat(first.toString()).doesNotContain("openid","open-one","session_key","appSecret");
        assertThat(first.at("/user/roles").get(0).asText()).isEqualTo("user");
        assertThat(first.get("phoneBindingRequired").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_agreement_consent",Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT access_hash FROM user_auth_session",String.class)).allMatch(h->h.length()==64 && !h.equals(access(first)));
        assertThat(data(send(as(get("/api/v1/me"),access(first)),null,200)).at("/id")).isEqualTo(first.at("/user/id"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_session",Long.class)).isZero();
        send(post("/api/v1/auth/wechat/login"),Map.of("code","third","agreements",List.of()),200);
    }
    @Test void missingConsentRollsBackEntireRegistrationAndSession() throws Exception {
        var r=send(post("/api/v1/auth/wechat/login"),Map.of("code","no-consent","agreements",List.of()),400);
        assertThat(json.readTree(r.getResponse().getContentAsByteArray()).get("code").asText()).isEqualTo("AGREEMENT_REQUIRED");
        for (var t:List.of("app_user","wechat_identity","user_settings","user_auth_session","user_agreement_consent","wechat_code_use","audit_log")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+t,Long.class)).as(t).isZero();
        }
    }
    @Test void consumedCodesCannotCreateAnotherSession() throws Exception {
        login("one-use");
        var r=send(post("/api/v1/auth/wechat/login"),Map.of("code","one-use","agreements",refs()),400);
        assertThat(json.readTree(r.getResponse().getContentAsByteArray()).get("code").asText()).isEqualTo("WX_CODE_INVALID");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_auth_session",Long.class)).isEqualTo(1);
    }
    @Test void simultaneousFirstLoginsUseUniqueIdentityAndOneAccount() throws Exception {
        try (var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->login("concurrent-a")); var b=pool.submit(()->login("concurrent-b"));
            var x=a.get();var y=b.get();
            assertThat(x.at("/user/id")).isEqualTo(y.at("/user/id"));
            assertThat(List.of(x.get("isNewUser").asBoolean(),y.get("isNewUser").asBoolean())).containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_auth_session",Long.class)).isEqualTo(2);
    }
    @Test void concurrentExistingIdentityLoginsDoNotDeadlockOrDuplicateConsent() throws Exception {
        var original=login("existing");
        var ready=new java.util.concurrent.CountDownLatch(4);
        var start=new java.util.concurrent.CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(4)) {
            var calls=new ArrayList<java.util.concurrent.Future<JsonNode>>();
            for (int i=0;i<4;i++) {
                String code="existing-concurrent-"+i;
                calls.add(pool.submit(()->{ready.countDown();start.await();return login(code);}));
            }
            assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
            for (var call:calls) {
                var result=call.get();assertThat(result.at("/user/id")).isEqualTo(original.at("/user/id"));
                assertThat(result.get("isNewUser").asBoolean()).isFalse();
            }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_agreement_consent",Long.class)).isEqualTo(2);
    }
    @Test void appIdAndOpenIdAreCaseSensitiveAndUnionIdDoesNotMergeAccounts() throws Exception {
        when(wechat.exchange("app-a")).thenReturn(new WechatGateway.Identity("wx0123456789abcdef","OpenId","shared-union"));
        when(wechat.exchange("app-b")).thenReturn(new WechatGateway.Identity("wxfedcba9876543210","OpenId","shared-union"));
        when(wechat.exchange("case")).thenReturn(new WechatGateway.Identity("wx0123456789abcdef","openid","shared-union"));
        var a=login("app-a"); var b=login("app-b"); var c=login("case");
        assertThat(Set.of(a.at("/user/id").asText(),b.at("/user/id").asText(),c.at("/user/id").asText())).hasSize(3);
    }
    @Test void userAndAdminCredentialsCannotCrossAudiencesIncludingRefresh() throws Exception {
        var u=login("user");
        var a=data(send(post("/api/v1/admin/auth/login"),Map.of("username","integration-admin","password","integration-password-12345","captchaTicket","integration-captcha-ticket-12345"),200));
        send(as(get("/api/v1/admin/me"),access(u)),null,401);
        send(as(get("/api/v1/me"),access(a)),null,403);
        send(post("/api/v1/auth/refresh"),Map.of("refreshToken",refresh(a)),401);
        send(post("/api/v1/admin/auth/refresh"),Map.of("refreshToken",refresh(u)),401);
        send(as(get("/api/v1/me"),refresh(u)),null,401);
        send(as(get("/api/v1/me/settings"),access(u)),null,200);
    }
    @Test void refreshRotatesBothTokensAndRejectsReplayEvenWithExpiredAuthorization() throws Exception {
        var u=login("refresh");
        jdbc.update("UPDATE user_auth_session SET access_expires_at=?",Timestamp.from(Instant.now().minusSeconds(60)));
        send(as(get("/api/v1/me"),access(u)),null,401);
        var rotated=data(send(as(post("/api/v1/auth/refresh"),access(u)),Map.of("refreshToken",refresh(u)),200));
        assertThat(access(rotated)).isNotEqualTo(access(u)); assertThat(refresh(rotated)).isNotEqualTo(refresh(u));
        send(post("/api/v1/auth/refresh"),Map.of("refreshToken",refresh(u)),401);
        send(as(get("/api/v1/me"),access(u)),null,401);
        send(as(get("/api/v1/me"),access(rotated)),null,200);
        send(as(post("/api/v1/auth/wechat/login"),"expired-or-invalid"),Map.of("code","fresh-login","agreements",refs()),200);
    }
    @Test void concurrentRefreshHasOnlyOneWinner() throws Exception {
        var u=login("race-refresh");
        Callable<Integer> refreshCall=()->mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("refreshToken",refresh(u))))).andReturn().getResponse().getStatus();
        try (var pool=Executors.newFixedThreadPool(2)) { var a=pool.submit(refreshCall);var b=pool.submit(refreshCall);
            assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(200,401); }
    }
    @Test void banAndDeletionStatusTakeEffectForLoginAccessAndRefresh() throws Exception {
        var u=login("bannable"); jdbc.update("UPDATE app_user SET status='banned'");
        for (var r:List.of(get("/api/v1/me"),get("/api/v1/me/settings"))) {
            var result=send(as(r,access(u)),null,403);
            assertThat(json.readTree(result.getResponse().getContentAsByteArray()).get("code").asText()).isEqualTo("ACCOUNT_BANNED");
        }
        send(post("/api/v1/auth/refresh"),Map.of("refreshToken",refresh(u)),403);
        send(post("/api/v1/auth/wechat/login"),Map.of("code","banned-login","agreements",refs()),403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_auth_session",Long.class)).isEqualTo(1);
        jdbc.update("UPDATE app_user SET status='deletion_pending'"); send(as(get("/api/v1/me"),access(u)),null,403);
    }
    @Test void logoutOnlyRevokesAnOwnedSessionAndOtherDevicesRemainActive() throws Exception {
        var a=login("device-a");var b=login("device-b");
        when(wechat.exchange("other-user")).thenReturn(new WechatGateway.Identity("wx0123456789abcdef","another",null));
        var other=login("other-user");
        send(as(post("/api/v1/auth/logout"),access(a)),Map.of("refreshToken",refresh(other)),200);
        send(as(get("/api/v1/me"),access(other)),null,200);
        send(as(post("/api/v1/auth/logout"),access(a)),Map.of("refreshToken",refresh(a)),200);
        send(as(get("/api/v1/me"),access(a)),null,401);
        send(post("/api/v1/auth/refresh"),Map.of("refreshToken",refresh(a)),401);
        send(as(get("/api/v1/me"),access(b)),null,200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='user.logout'",Long.class)).isEqualTo(1);
    }
    @Test void settingsValidateTypesBoundsReferencesAndOptimisticVersion() throws Exception {
        var u=login("settings");String token=access(u);
        var initial=data(send(as(get("/api/v1/me/settings"),token),null,200));
        var body=json.convertValue(initial,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        body.put("theme","forest"); var changed=data(send(as(put("/api/v1/me/settings"),token),body,200));
        assertThat(changed.get("version").asInt()).isEqualTo(2);
        assertThat(data(send(as(get("/api/v1/me"),token),null,200)).get("theme").asText()).isEqualTo("forest");
        send(as(put("/api/v1/me/settings"),token),body,409);
        body.put("version",2);body.put("defaultSpeed",1.1);send(as(put("/api/v1/me/settings"),token),body,400);
        body.put("defaultSpeed",1.0);body.put("practiceReminderEnabled",true);send(as(put("/api/v1/me/settings"),token),body,400);
        body.put("practiceReminderTime","25:10");send(as(put("/api/v1/me/settings"),token),body,400);
        body.put("practiceReminderTime","09:30");body.put("endBellAssetId","forged");send(as(put("/api/v1/me/settings"),token),body,400);
        body.put("endBellAssetId",null);body.put("backgroundAudio","true");send(as(put("/api/v1/me/settings"),token),body,400);
        assertThat(data(send(as(get("/api/v1/me/settings"),token),null,200)).get("version").asInt()).isEqualTo(2);
    }
    @Test void currentAgreementIsRequiredAgainAfterVersionUpdateAndHistoryIsRetained() throws Exception {
        login("old");
        jdbc.update("INSERT INTO agreement_version(agreement_type,version,title,content,effective_at,published,test_only) VALUES ('privacy','approved-v2','privacy','approved content',?,TRUE,FALSE)",Timestamp.from(Instant.now().minusSeconds(1)));
        send(post("/api/v1/auth/wechat/login"),Map.of("code","stale","agreements",refs()),409);
        verify(wechat,never()).exchange("stale");
        send(post("/api/v1/auth/wechat/login"),Map.of("code","no-new-consent","agreements",List.of()),400);
        send(post("/api/v1/auth/wechat/login"),Map.of("code","new-consent","agreements",List.of(Map.of("type","privacy","version","approved-v2"))),200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_agreement_consent",Long.class)).isEqualTo(3);
        send(get("/api/v1/agreements/privacy?version=local-test-2026-11-09"),null,200);
    }
    @Test void testAgreementsAreNeverAvailableOutsideLocalAndFutureVersionsAreHidden() throws Exception {
        var production=new AgreementService(jdbc,clock,new MockEnvironment());
        assertThatThrownBy(()->production.required()).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(()->production.get(UserModels.AgreementType.privacy,"local-test-2026-11-09"))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        jdbc.update("INSERT INTO agreement_version(agreement_type,version,title,content,effective_at,published,test_only) VALUES ('privacy','future','title','text',?,TRUE,FALSE)",Timestamp.from(Instant.now().plusSeconds(86400)));
        send(get("/api/v1/agreements/privacy?version=future"),null,404);
        assertThat(data(send(get("/api/v1/agreements/privacy"),null,200)).get("title").asText()).contains("仅本地测试");
    }
    @Test void malformedRequestsAndProviderFailuresHaveNoPartialWrites() throws Exception {
        for (String raw:List.of("{\"code\":1,\"agreements\":[]}","{\"code\":\"a\",\"code\":\"b\",\"agreements\":[]}","{\"code\":\"a\",\"agreements\":[]} {}","{\"code\":\"a\",\"agreements\":[],\"openid\":\"fake\"}")) {
            mvc.perform(post("/api/v1/auth/wechat/login").contentType(MediaType.APPLICATION_JSON).content(raw)).andExpect(status().isBadRequest());
        }
        send(post("/api/v1/auth/wechat/login"),Map.of("code","duplicates","agreements",List.of(refs().getFirst(),refs().getFirst())),400);
        when(wechat.exchange("unavailable")).thenThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"PROVIDER_UNAVAILABLE","微信登录服务暂不可用"));
        send(post("/api/v1/auth/wechat/login"),Map.of("code","unavailable","agreements",refs()),503);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Long.class)).isZero();
    }
    @Test void refreshExpirationAndWrongAudienceAreRejected() throws Exception {
        var u=login("expires");
        jdbc.update("UPDATE user_auth_session SET refresh_expires_at=?",Timestamp.from(Instant.now().minusSeconds(60)));
        send(post("/api/v1/auth/refresh"),Map.of("refreshToken",refresh(u)),401);
        send(as(get("/api/v1/me"),access(u)),null,200);
    }
    @Test void newResponsesMatchOpenApiFieldSetsAndDateFormats() throws Exception {
        var spec=json.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("contract.path"))));
        var u=login("contract");
        assertFields(u,spec.at("/components/schemas/LoginResult"));
        assertFields(u.get("user"),spec.at("/components/schemas/User"));
        assertFields(u.at("/user/membership"),spec.at("/components/schemas/Membership"));
        assertFields(data(send(as(get("/api/v1/me/settings"),access(u)),null,200)),spec.at("/components/schemas/Settings"));
        var agreement=data(send(get("/api/v1/agreements/user"),null,200));
        assertFields(agreement,spec.at("/components/schemas/Agreement"));
        Instant.parse(agreement.get("effectiveAt").asText());
        var rotated=data(send(post("/api/v1/auth/refresh"),Map.of("refreshToken",refresh(u)),200));
        assertFields(rotated,spec.at("/components/schemas/AuthTokens"));
        var logout=send(as(post("/api/v1/auth/logout"),access(rotated)),Map.of("refreshToken",refresh(rotated)),200);
        assertFields(data(logout),spec.at("/components/schemas/OK"));
        var root=json.readTree(logout.getResponse().getContentAsByteArray());
        Instant.parse(root.get("serverTime").asText());
        assertThat(root.get("requestId").asText()).isEqualTo(logout.getResponse().getHeader("X-Request-ID"));
    }
    private void assertFields(JsonNode actual,JsonNode schema) {
        assertThat(schema.isMissingNode()).isFalse();
        for (var field:schema.get("required")) { assertThat(actual.has(field.asText())).as(field.asText()).isTrue(); }
        actual.fieldNames().forEachRemaining(field->assertThat(schema.get("properties").has(field)).as(field).isTrue());
    }

}
