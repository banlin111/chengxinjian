package com.chengxinjian;

import com.chengxinjian.common.AuditService;
import com.chengxinjian.media.*;
import com.chengxinjian.user.UserSessionService;
import com.fasterxml.jackson.databind.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:media;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USERNAME:sa}","spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
    "app.auth.bootstrap-username=integration-admin","app.auth.bootstrap-password=integration-password-12345",
    "app.auth.local-captcha-ticket=integration-captcha-ticket-12345","app.media.worker-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MediaIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserSessionService sessions;
    @Autowired MediaWorker worker;
    @MockitoBean MediaStorage storage;
    @MockitoSpyBean AuditService audit;
    byte[] png;
    @BeforeEach void reset() throws Exception {
        jdbc.update("UPDATE admin_account SET teacher_id=NULL,enabled=TRUE,data_scope='all'");
        for(String table:List.of("media_processing_job","media_upload_request","teacher_follow","content_teacher","media_asset","user_agreement_consent","user_auth_session","wechat_identity","user_settings","app_user","idempotency_record","login_throttle","audit_log","auth_session")) jdbc.update("DELETE FROM "+table);
        jdbc.update("DELETE FROM admin_role_permission WHERE role_id='role-content-manager'");
        for(String p:List.of("content:read","content:write"))jdbc.update("INSERT INTO admin_role_permission(role_id,permission_code) VALUES ('role-content-manager',?)",p);
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);png=out.toByteArray();
        when(storage.ticket(anyString(),anyString(),anyString(),anyLong(),any())).thenAnswer(i->new MediaModels.Ticket(i.getArgument(0),"https://upload.example.test/","POST",Map.of("key",i.getArgument(1),"q-signature","fixture-scoped-signature"),"file",i.getArgument(4),i.getArgument(3)));
        doAnswer(i->{Files.write((Path)i.getArgument(1),png);return null;}).when(storage).download(anyString(),any(),anyLong(),anyString());
        when(storage.preview(anyString(),any())).thenReturn("https://private.example.test/temporary");
    }
    String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    Map<String,Object> input(String purpose) throws Exception {return new LinkedHashMap<>(Map.of("purpose",purpose,"filename","test.png","mimeType","image/png","sizeBytes",png.length,"sha256",hash(png)));}
    JsonNode send(MockHttpServletRequestBuilder req,Object body,int expected) throws Exception {
        if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return json.readTree(mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray());
    }
    MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder req,String token) {return req.header("Authorization","Bearer "+token);}
    String admin() throws Exception {return send(post("/api/v1/admin/auth/login"),Map.of("username","integration-admin","password","integration-password-12345","captchaTicket","integration-captcha-ticket-12345"),200).at("/data/accessToken").asText();}
    String user() {
        String id=UUID.randomUUID().toString();var now=Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO app_user(id,nickname,status,version,created_at,updated_at,last_login_at) VALUES (?,'媒体用户','active',1,?,?,?)",id,now,now,now);
        return sessions.create(id).accessToken();
    }
    JsonNode ticket(String token,String key,Map<String,Object> input,int status) throws Exception {return send(as(post("/api/v1/uploads").header("Idempotency-Key",key),token),input,status);}
    String create(String token) throws Exception {return ticket(token,UUID.randomUUID().toString(),input("avatar"),200).at("/data/assetId").asText();}
    JsonNode complete(String token,String id,String key,int status) throws Exception {return send(as(post("/api/v1/uploads/"+id+"/complete").header("Idempotency-Key",key),token),Map.of("sha256",hash(png)),status);}
    JsonNode getAsset(String token,String id,int status) throws Exception {return send(as(get("/api/v1/assets/"+id),token),null,status);}
    @Test void uploadRemainsPrivateUntilActualFileValidationAndPromotesAnImmutableObject() throws Exception {
        String token=admin(),id=create(token);assertThat(getAsset(token,id,200).at("/data/status").asText()).isEqualTo("uploading");
        complete(token,id,UUID.randomUUID().toString(),200);assertThat(getAsset(token,id,200).at("/data/status").asText()).isEqualTo("processing");
        assertThat(worker.runOnce()).isTrue();var asset=getAsset(token,id,200).get("data");
        assertThat(asset.get("status").asText()).isEqualTo("ready");assertThat(asset.get("publicUrl").isNull()).isTrue();
        assertThat(asset.has("objectKey")).isFalse();assertThat(asset.has("sha256")).isFalse();assertThat(asset.has("ownerAdminId")).isFalse();
        verify(storage).put(eq("validated/"+id+"/source"),any(),eq("image/png"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='media.validation.ready'",Long.class)).isEqualTo(1);
    }
    @Test void ticketReplaysOriginalSnapshotAndRejectsChangedRequest() throws Exception {
        String token=admin(),key=UUID.randomUUID().toString();var body=input("avatar");var first=ticket(token,key,body,200);
        assertThat(ticket(token,key,body,200).get("data")).isEqualTo(first.get("data"));
        body.put("filename","changed.png");ticket(token,key,body,409);verify(storage,times(1)).ticket(anyString(),anyString(),anyString(),anyLong(),any());
    }
    @Test void concurrentTicketRequestsReserveOnlyOneAsset() throws Exception {
        String token=admin(),key=UUID.randomUUID().toString();var input=input("avatar");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->mvc.perform(as(post("/api/v1/uploads").header("Idempotency-Key",key),token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(input))).andReturn().getResponse().getStatus());
            var b=pool.submit(()->mvc.perform(as(post("/api/v1/uploads").header("Idempotency-Key",key),token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(input))).andReturn().getResponse().getStatus());
            assertThat(List.of(a.get(),b.get())).allMatch(s->s==200 || s==409).contains(200);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_asset",Long.class)).isEqualTo(1);ticket(token,key,input,200);
    }
    @Test void completeReplaysInitialResultAfterWorkerAndDoesNotEnqueueTwice() throws Exception {
        String token=admin(),id=create(token),key=UUID.randomUUID().toString();var first=complete(token,id,key,200);worker.runOnce();
        assertThat(complete(token,id,key,200).get("data")).isEqualTo(first.get("data"));assertThat(worker.runOnce()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_processing_job",Long.class)).isEqualTo(1);
        complete(token,id,UUID.randomUUID().toString(),200);assertThat(worker.runOnce()).isFalse();
    }
    @Test void completionRequiresDeclaredHashAndUuidAndRejectsStrictJsonViolations() throws Exception {
        String token=admin(),id=create(token);
        send(as(post("/api/v1/uploads/"+id+"/complete").header("Idempotency-Key",UUID.randomUUID().toString()),token),Map.of("sha256","a".repeat(64)),409);
        complete(token,id,"bad-key",400);var body=input("avatar");body.put("unknown",true);ticket(token,UUID.randomUUID().toString(),body,400);
        body.remove("unknown");body.put("sizeBytes","123");ticket(token,UUID.randomUUID().toString(),body,400);
        assertThat(worker.runOnce()).isFalse();
    }
    @Test void wrongHashAndSpoofedMimeAreRejectedByDownloadedBytes() throws Exception {
        String token=admin(),id=create(token);complete(token,id,UUID.randomUUID().toString(),200);
        doAnswer(i->{Files.write((Path)i.getArgument(1),new byte[png.length]);return null;}).when(storage).download(anyString(),any(),anyLong(),anyString());
        worker.runOnce();assertThat(getAsset(token,id,200).at("/data/status").asText()).isEqualTo("rejected");verify(storage,never()).put(anyString(),any(),anyString());
        assertThat(jdbc.queryForObject("SELECT failure_code FROM media_asset WHERE id=?",String.class,id)).isEqualTo("SHA256_MISMATCH");
    }
    @Test void matchingHashDoesNotLetNonImageBytesBecomeReady() throws Exception {
        String token=admin();byte[] fake="not a real image".getBytes();var body=input("avatar");body.put("sizeBytes",fake.length);body.put("sha256",hash(fake));
        String id=ticket(token,UUID.randomUUID().toString(),body,200).at("/data/assetId").asText();
        send(as(post("/api/v1/uploads/"+id+"/complete").header("Idempotency-Key",UUID.randomUUID().toString()),token),Map.of("sha256",hash(fake)),200);
        doAnswer(i->{Files.write((Path)i.getArgument(1),fake);return null;}).when(storage).download(anyString(),any(),anyLong(),anyString());
        worker.runOnce();assertThat(getAsset(token,id,200).at("/data/status").asText()).isEqualTo("rejected");verify(storage,never()).put(anyString(),any(),anyString());
    }
    @Test void usersCannotReadOrCompleteOthersFilesAndCannotUploadCourseMedia() throws Exception {
        String a=user(),b=user(),admin=admin(),id=create(a);getAsset(b,id,404);complete(b,id,UUID.randomUUID().toString(),404);getAsset(admin,id,403);
        ticket(a,UUID.randomUUID().toString(),input("cover"),403);
        send(as(post("/api/v1/admin/assets/"+id+"/preview"),a),null,401);
        send(as(get("/api/v1/me"),admin),null,403);send(get("/api/v1/assets/"+id),null,401);
    }
    @Test void disabledAccountAndRevokedPermissionsApplyToMediaImmediately() throws Exception {
        String token=admin(),id=create(token);jdbc.update("DELETE FROM admin_role_permission WHERE permission_code='content:write'");complete(token,id,UUID.randomUUID().toString(),403);
        jdbc.update("UPDATE admin_account SET enabled=FALSE");getAsset(token,id,401);
    }
    @Test void ownContentCannotUseGlobalOrOtherAdministratorsAssets() throws Exception {
        String token=admin(),id=create(token);jdbc.update("UPDATE admin_account SET data_scope='own_content',teacher_id=NULL");
        getAsset(token,id,403);ticket(token,UUID.randomUUID().toString(),input("avatar"),403);
    }
    @Test void previewIsAdminOnlyShortLivedAndRequiresValidatedMedia() throws Exception {
        String token=admin(),id=create(token);send(as(post("/api/v1/admin/assets/"+id+"/preview"),token),null,409);
        complete(token,id,UUID.randomUUID().toString(),200);worker.runOnce();
        var preview=send(as(post("/api/v1/admin/assets/"+id+"/preview"),token),null,200).get("data");
        assertThat(Instant.parse(preview.get("expiresAt").asText())).isBefore(Instant.now().plusSeconds(125));
        verify(storage).preview(eq("validated/"+id+"/source"),any());
    }
    @Test void providerFailurePreservesReservationAndAllowsSameKeyToRetry() throws Exception {
        String token=admin(),key=UUID.randomUUID().toString();when(storage.ticket(anyString(),anyString(),anyString(),anyLong(),any())).thenThrow(new com.chengxinjian.common.BusinessException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"MEDIA_PROVIDER_UNAVAILABLE","暂不可用"));
        ticket(token,key,input("avatar"),503);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_asset",Long.class)).isEqualTo(1);
        org.mockito.Mockito.reset(storage);when(storage.ticket(anyString(),anyString(),anyString(),anyLong(),any())).thenAnswer(i->new MediaModels.Ticket(i.getArgument(0),"https://upload.example.test","POST",Map.of(),"file",i.getArgument(4),i.getArgument(3)));
        ticket(token,key,input("avatar"),200);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_asset",Long.class)).isEqualTo(1);
    }
    @Test void transientStorageFailureRetriesDurablyAndCannotMarkMediaReady() throws Exception {
        String token=admin(),id=create(token);complete(token,id,UUID.randomUUID().toString(),200);
        doThrow(new IllegalStateException("simulated provider failure")).when(storage).put(anyString(),any(),anyString());worker.runOnce();
        assertThat(getAsset(token,id,200).at("/data/status").asText()).isEqualTo("processing");assertThat(jdbc.queryForObject("SELECT state FROM media_processing_job WHERE asset_id=?",String.class,id)).isEqualTo("pending");
        doNothing().when(storage).put(anyString(),any(),anyString());jdbc.update("UPDATE media_processing_job SET next_attempt_at=?",Timestamp.from(Instant.now().minusSeconds(10)));worker.runOnce();
        assertThat(getAsset(token,id,200).at("/data/status").asText()).isEqualTo("ready");
    }
    @Test void expiredWorkerLeaseCanBeRecoveredAfterRestart() throws Exception {
        String token=admin(),id=create(token);complete(token,id,UUID.randomUUID().toString(),200);
        jdbc.update("UPDATE media_processing_job SET state='running',lease_token='previous-worker',lease_until=? WHERE asset_id=?",Timestamp.from(Instant.now().minusSeconds(5)),id);
        assertThat(worker.runOnce()).isTrue();assertThat(getAsset(token,id,200).at("/data/status").asText()).isEqualTo("ready");
    }
    @Test void cleanupDeletesOnlyExpiredIsolationObjectsAndKeepsHistoricalAssets() throws Exception {
        String token=admin(),id=create(token);complete(token,id,UUID.randomUUID().toString(),200);worker.runOnce();
        jdbc.update("UPDATE media_asset SET upload_expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(2000)),id);worker.cleanupOnce();worker.cleanupOnce();
        verify(storage,times(1)).delete("quarantine/"+id+"/source");verify(storage,never()).delete("validated/"+id+"/source");assertThat(getAsset(token,id,200).at("/data/status").asText()).isEqualTo("ready");
    }
    @Test void expiredUploadCannotCompleteAndOversizedOrUnsafeRequestsNeverReachStorage() throws Exception {
        String token=admin(),id=create(token);jdbc.update("UPDATE media_asset SET upload_expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(5)),id);complete(token,id,UUID.randomUUID().toString(),409);
        var input=input("avatar");input.put("sizeBytes",11*1024*1024);ticket(token,UUID.randomUUID().toString(),input,400);
        input.put("sizeBytes",png.length);input.put("filename","../../test.png");ticket(token,UUID.randomUUID().toString(),input,400);verify(storage,times(1)).ticket(anyString(),anyString(),anyString(),anyLong(),any());
    }
    @Test void auditFailureRollsBackCompletionAndJobTogether() throws Exception {
        String token=admin(),id=create(token);doThrow(new org.springframework.dao.DataIntegrityViolationException("simulated audit failure")).when(audit).record(anyString(),eq("media.upload.complete"),eq(id),isNull());
        complete(token,id,UUID.randomUUID().toString(),503);assertThat(getAsset(token,id,200).at("/data/status").asText()).isEqualTo("uploading");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_processing_job",Long.class)).isZero();
    }
    @Test void competingWorkersCannotPromoteTheSameAssetTwice() throws Exception {
        String token=admin(),id=create(token);complete(token,id,UUID.randomUUID().toString(),200);
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        doAnswer(i->{entered.countDown();if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException();Files.write((Path)i.getArgument(1),png);return null;}).when(storage).download(anyString(),any(),anyLong(),anyString());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(worker::runOnce);assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try {assertThat(pool.submit(worker::runOnce).get()).isFalse();}finally{release.countDown();}
            assertThat(first.get()).isTrue();
        }
        verify(storage,times(1)).put(anyString(),any(),anyString());assertThat(getAsset(token,id,200).at("/data/status").asText()).isEqualTo("ready");
    }
    @Test void exhaustedProviderRetriesCanBeExplicitlyResubmittedWithANewCompletionKey() throws Exception {
        String token=admin(),id=create(token);complete(token,id,UUID.randomUUID().toString(),200);
        doThrow(new IllegalStateException("temporary outage")).when(storage).put(anyString(),any(),anyString());
        for(int i=0;i<5;i++) {jdbc.update("UPDATE media_processing_job SET next_attempt_at=?",Timestamp.from(Instant.now().minusSeconds(5)));worker.runOnce();}
        assertThat(jdbc.queryForObject("SELECT state FROM media_processing_job WHERE asset_id=?",String.class,id)).isEqualTo("failed");
        doNothing().when(storage).put(anyString(),any(),anyString());complete(token,id,UUID.randomUUID().toString(),200);worker.runOnce();
        assertThat(getAsset(token,id,200).at("/data/status").asText()).isEqualTo("ready");
    }
    @Test void uploadContractAcceptsProviderDictionaryWhileClosingTheTicketEnvelope() throws Exception {
        String token=admin();var ticket=ticket(token,UUID.randomUUID().toString(),input("avatar"),200).get("data");
        var spec=json.readTree(Files.readString(Path.of(System.getProperty("contract.path"))));var schemas=spec.at("/components/schemas");
        String dictionary=schemas.get("UploadTicket").at("/properties/formData/$ref").asText();
        assertThat(spec.at(dictionary.substring(1)).at("/additionalProperties/type").asText()).isEqualTo("string");
        schemas.fieldNames().forEachRemaining(name->assertThat(name).matches("[a-zA-Z0-9._-]+"));
        var preview=spec.at("/paths/~1admin~1assets~1{id}~1preview/post/parameters");
        assertThat(preview.toString()).doesNotContain("Idempotency-Key");
        assertThat(schemas.get("UploadTicket").get("additionalProperties").asBoolean()).isFalse();
        Set<String> fields=new HashSet<>();ticket.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("assetId","uploadUrl","method","formData","fileField","expiresAt","maxSizeBytes");
        assertThat(ticket.get("formData").has("q-signature")).isTrue();
    }
}
