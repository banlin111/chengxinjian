package com.chengxinjian.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.wechat")
public record WechatProperties(String appId, String appSecret) {
    // Records otherwise expose secrets through their generated toString().
    @Override public String toString() { return "WechatProperties[redacted]"; }
}
