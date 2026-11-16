package com.chengxinjian.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.phone")
public record PhoneProviderProperties(String smsAppId,String sign,String templateId,String region,
        String secretId,String secretKey,long captchaAppId,String captchaAppSecret,String captchaSecretId,String captchaSecretKey) {
    boolean smsConfigured() {
        return smsAppId!=null && !smsAppId.isBlank() && !sign.isBlank() && !templateId.isBlank() &&
            !secretId.isBlank() && !secretKey.isBlank() && !region.isBlank();
    }
    @Override public String toString() { return "PhoneProviderProperties[redacted]"; }
}
