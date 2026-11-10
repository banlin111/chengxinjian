package com.chengxinjian.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sts.v20180813.StsClient;
import com.tencentcloudapi.sts.v20180813.models.GetFederationTokenRequest;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(MediaProperties.class)
public class CosMediaStorage implements MediaStorage {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(CosMediaStorage.class);
    private final MediaProperties properties;
    private final ObjectMapper json;
    private final Clock clock;
    private COSClient cos;
    private StsClient sts;
    public CosMediaStorage(MediaProperties properties,ObjectMapper json,Clock clock) { this.properties=properties;this.json=json;this.clock=clock; }
    private synchronized void initialize() {
        if (cos!=null) return;
        if (!"tencent".equals(properties.provider()) || properties.bucket()==null || !properties.bucket().matches("[a-z0-9-]+-[0-9]+")
            || properties.region()==null || !properties.region().matches("[a-z]+-[a-z]+(?:-[0-9]+)?")
            || properties.secretId()==null || properties.secretId().isBlank() || properties.secretKey()==null || properties.secretKey().isBlank()) throw MediaErrors.unavailable();
        var config=new ClientConfig(new Region(properties.region()));
        config.setHttpProtocol(HttpProtocol.https);config.setConnectionTimeout(3000);config.setSocketTimeout(15000);
        config.setMaxErrorRetry(0);
        var http=new HttpProfile();http.setEndpoint("sts.tencentcloudapi.com");http.setConnTimeout(3);http.setReadTimeout(5);
        var profile=new ClientProfile();profile.setHttpProfile(http);
        sts=new StsClient(new Credential(properties.secretId(),properties.secretKey()),properties.region(),profile);
        cos=new COSClient(new BasicCOSCredentials(properties.secretId(),properties.secretKey()),config);
    }
    String resource(String key) {
        String appId=properties.bucket().substring(properties.bucket().lastIndexOf('-')+1);
        return "qcs::cos:"+properties.region()+":uid/"+appId+":"+properties.bucket()+"/"+key;
    }
    @Override public MediaModels.Ticket ticket(String assetId,String key,String mime,long size,Instant expires) {
        initialize();
        try {
            String scope=json.writeValueAsString(Map.of("version","2.0","statement",List.of(Map.of(
                "effect","allow","action",List.of("name/cos:PostObject"),"resource",List.of(resource(key))))));
            var request=new GetFederationTokenRequest();request.setName("cxjUpload");request.setDurationSeconds(1800L);
            request.setPolicy(java.net.URLEncoder.encode(scope,StandardCharsets.UTF_8));
            var response=sts.GetFederationToken(request);var credentials=response.getCredentials();
            Instant effectiveExpiry=expires.isBefore(Instant.ofEpochSecond(response.getExpiredTime())) ? expires : Instant.ofEpochSecond(response.getExpiredTime());
            return signTicket(assetId,key,mime,size,effectiveExpiry,clock.instant().minusSeconds(30),credentials.getTmpSecretId(),credentials.getTmpSecretKey(),credentials.getToken());
        } catch (Exception e) { throw failure(e); }
    }
    // Exact object, MIME and byte count are signed. No permanent ID/key or temporary SecretKey is sent to clients.
    MediaModels.Ticket signTicket(String assetId,String key,String mime,long size,Instant expires,Instant start,String id,String secret,String token) throws Exception {
        String time=start.getEpochSecond()+";"+expires.getEpochSecond();
        var fields=new LinkedHashMap<String,String>();fields.put("key",key);fields.put("Content-Type",mime);
        fields.put("acl","private");fields.put("Cache-Control","private, no-store");
        fields.put("success_action_status","204");fields.put("x-cos-server-side-encryption","AES256");
        fields.put("q-sign-algorithm","sha1");fields.put("q-ak",id);fields.put("q-key-time",time);fields.put("x-cos-security-token",token);
        var conditions=new ArrayList<Object>();conditions.add(Map.of("bucket",properties.bucket()));
        for (var entry:fields.entrySet()) if (!"q-key-time".equals(entry.getKey())) conditions.add(Map.of(entry.getKey(),entry.getValue()));
        conditions.add(Map.of("q-sign-time",time));conditions.add(List.of("content-length-range",size,size));
        String policy=json.writeValueAsString(Map.of("expiration",expires.toString(),"conditions",conditions));
        String signKey=hmac(secret,time);
        String policyHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(policy.getBytes(StandardCharsets.UTF_8)));
        fields.put("policy",Base64.getEncoder().encodeToString(policy.getBytes(StandardCharsets.UTF_8)));
        fields.put("q-signature",hmac(signKey,policyHash));
        return new MediaModels.Ticket(assetId,"https://"+properties.bucket()+".cos."+properties.region()+".myqcloud.com/","POST",Map.copyOf(fields),"file",expires,size);
    }
    private String hmac(String secret,String value) throws Exception {
        var mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA1"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
    private com.chengxinjian.common.BusinessException failure(Exception error) {
        String code=error instanceof com.tencentcloudapi.common.exception.TencentCloudSDKException e ? e.getErrorCode() : error.getClass().getSimpleName();
        if(code==null || !code.matches("[A-Za-z0-9_.]{1,100}"))code="ProviderError";
        log.warn("Media provider unavailable: {}",code);return MediaErrors.unavailable();
    }
    @Override public void download(String key,Path destination,long expectedSize,String mime) {
        initialize();
        try {
            try (var object=cos.getObject(properties.bucket(),key)) {
                if (object.getObjectMetadata().getContentLength()!=expectedSize || !mime.equals(object.getObjectMetadata().getContentType())) throw new MediaErrors.Invalid("OBJECT_METADATA_MISMATCH");
                try (var input=object.getObjectContent();var output=Files.newOutputStream(destination)) {
                    byte[] buffer=new byte[65536];long total=0;int read;
                    while ((read=input.read(buffer))!=-1) { total+=read;if(total>expectedSize) throw new MediaErrors.Invalid("SIZE_MISMATCH");output.write(buffer,0,read); }
                    if (total!=expectedSize) throw new MediaErrors.Invalid("SIZE_MISMATCH");
                }
            }
        } catch (MediaErrors.Invalid e) { throw e; }
        catch (Exception e) { throw MediaErrors.unavailable(); }
    }
    @Override public void put(String key,Path source,String mime) {
        initialize();
        try {
            var metadata=new ObjectMetadata();metadata.setContentType(mime);metadata.setContentLength(Files.size(source));
            metadata.setSSEAlgorithm("AES256");metadata.setCacheControl("private, no-store");
            var request=new PutObjectRequest(properties.bucket(),key,source.toFile());request.setMetadata(metadata);cos.putObject(request);
        } catch (Exception e) { throw MediaErrors.unavailable(); }
    }
    @Override public String preview(String key,Instant expires) {
        initialize();
        try { return cos.generatePresignedUrl(properties.bucket(),key,Date.from(expires)).toExternalForm(); }
        catch (Exception e) { throw MediaErrors.unavailable(); }
    }
    @Override public void delete(String key) {
        initialize();try { cos.deleteObject(properties.bucket(),key); } catch (Exception e) { throw MediaErrors.unavailable(); }
    }
    @PreDestroy public synchronized void shutdown() { if (cos!=null) cos.shutdown(); }
}
