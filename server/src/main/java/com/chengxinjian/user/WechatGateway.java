package com.chengxinjian.user;

public interface WechatGateway {
    // session_key is deliberately not retained: this flow only needs the identity.
    record Identity(String appId, String openId, String unionId) {
        @Override public String toString() { return "Identity[redacted]"; }
    }
    Identity exchange(String code);
}
