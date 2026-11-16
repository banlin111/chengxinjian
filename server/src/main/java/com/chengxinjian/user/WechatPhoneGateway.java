package com.chengxinjian.user;

import com.chengxinjian.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class WechatPhoneGateway {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final WechatProperties config;
    private final ObjectMapper json;
    private final Clock clock;
    private String token;
    private Instant expires=Instant.EPOCH;
    WechatPhoneGateway(WechatProperties config,ObjectMapper json,Clock clock) { this.config=config; this.json=json; this.clock=clock; }
    private BusinessException unavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"PROVIDER_UNAVAILABLE","微信手机号服务暂不可用");
    }
    private JsonNode post(String url,Map<String,?> fields) {
        try {
            var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6)).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(fields))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode()!=200 || response.body().length()>16384) throw unavailable();
            var result=json.readTree(response.body());
            if (!result.isObject()) throw unavailable();
            return result;
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (Exception ex) { throw unavailable(); }
    }
    private synchronized String appToken() {
        if (expires.isAfter(clock.instant()) && token!=null) return token;
        if (config.appId()==null || !config.appId().matches("wx[0-9a-fA-F]{16}") || config.appSecret()==null || config.appSecret().isBlank()) throw unavailable();
        var result=post("https://api.weixin.qq.com/cgi-bin/stable_token",
            Map.of("grant_type","client_credential","appid",config.appId(),"secret",config.appSecret(),"force_refresh",false));
        String next=result.path("access_token").textValue();
        int ttl=result.path("expires_in").asInt();
        if (next==null || next.isBlank() || ttl<=60 || result.path("errcode").asInt()!=0) throw unavailable();
        expires=clock.instant().plusSeconds(Math.min(ttl,7200)-60);
        token=next;
        return next;
    }
    public String exchange(String code) {
        String used=appToken();
        var result=post("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token="+URLEncoder.encode(used,StandardCharsets.UTF_8),Map.of("code",code));
        if (!result.path("errcode").canConvertToInt() || !result.path("errcode").isIntegralNumber()) throw unavailable();
        int error=result.path("errcode").intValue();
        if (error==40001 || error==42001) synchronized(this) { if (used.equals(token)) expires=Instant.EPOCH; }
        if (error==40029 || error==40163) throw BusinessException.invalid("手机号授权凭证无效或已使用");
        if (error!=0) throw unavailable();
        return verifiedNumber(result.path("phone_info"));
    }
    String verifiedNumber(JsonNode info) {
        String phone=info.path("purePhoneNumber").textValue();
        if (!config.appId().equals(info.path("watermark").path("appid").textValue())) throw unavailable();
        if (!"86".equals(info.path("countryCode").asText()) || phone==null || !phone.matches("1[3-9][0-9]{9}")) throw BusinessException.invalid("仅支持中国大陆手机号");
        return phone;
    }
}
