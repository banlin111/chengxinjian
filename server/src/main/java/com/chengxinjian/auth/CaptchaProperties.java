package com.chengxinjian.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.captcha")
public record CaptchaProperties(long appId,String appSecretKey,String secretId,String secretKey) {
    public boolean configured() { return appId>0 && !appSecretKey.isBlank() && !secretId.isBlank() && !secretKey.isBlank(); }
    @Override public String toString() { return "CaptchaProperties[redacted]"; }
}
