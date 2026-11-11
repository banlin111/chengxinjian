package com.chengxinjian.auth;

public interface CaptchaGateway {
    void verify(String ticket,String randstr,String remoteAddress);
}
