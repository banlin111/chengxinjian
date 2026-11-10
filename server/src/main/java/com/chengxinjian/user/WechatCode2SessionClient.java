package com.chengxinjian.user;

import com.chengxinjian.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(WechatProperties.class)
public class WechatCode2SessionClient implements WechatGateway {
    private final WechatProperties config;
    private final ObjectMapper json;
    private final HttpClient http;
    @org.springframework.beans.factory.annotation.Autowired
    public WechatCode2SessionClient(WechatProperties config, ObjectMapper json) {
        this(config, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    WechatCode2SessionClient(WechatProperties config, ObjectMapper json, HttpClient http) {
        this.config=config; this.json=json; this.http=http;
    }
    @Override public Identity exchange(String code) {
        if (config.appId()==null || !config.appId().matches("wx[0-9a-fA-F]{16}")
            || config.appSecret()==null || !config.appSecret().matches("[0-9a-fA-F]{32}")) { throw unavailable(); }
        try {
            // Fixed HTTPS host; never log request URI/response bodies/exceptions (query contains secrets).
            var uri = URI.create("https://api.weixin.qq.com/sns/jscode2session?appid="+encode(config.appId())
                +"&secret="+encode(config.appSecret())+"&js_code="+encode(code)+"&grant_type=authorization_code");
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode()!=200 || response.body().length()>16384) { throw unavailable(); }
            return parse(response.body());
        } catch (BusinessException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (Exception e) { throw unavailable(); }
    }
    Identity parse(String body) {
        try {
            var root=json.readTree(body);
            if (!root.isObject() || (root.has("errcode") && !root.get("errcode").isIntegralNumber())) { throw unavailable(); }
            int error=root.path("errcode").asInt(0);
            if (error==40029 || error==40163) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,"WX_CODE_INVALID","微信登录凭证无效或已使用，请重新登录");
            }
            if (error==40226) { throw new BusinessException(HttpStatus.FORBIDDEN,"WX_LOGIN_BLOCKED","微信暂不允许此账号登录"); }
            if (error!=0) { throw unavailable(); }
            var openId=root.get("openid"); var key=root.get("session_key"); var unionId=root.get("unionid");
            if (openId==null || !openId.isTextual() || openId.asText().isBlank() || openId.asText().length()>128
                || key==null || !key.isTextual() || key.asText().isBlank()
                || (unionId!=null && (!unionId.isTextual() || unionId.asText().isBlank() || unionId.asText().length()>128))) { throw unavailable(); }
            return new Identity(config.appId(),openId.asText(),unionId==null ? null : unionId.asText());
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw unavailable(); }
    }
    private static String encode(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
    static BusinessException unavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"PROVIDER_UNAVAILABLE","微信登录服务暂不可用，请稍后重新取得登录凭证");
    }
}
