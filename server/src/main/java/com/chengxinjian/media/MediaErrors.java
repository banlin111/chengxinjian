package com.chengxinjian.media;

import com.chengxinjian.common.BusinessException;
import org.springframework.http.HttpStatus;

final class MediaErrors {
    private MediaErrors() {}
    static BusinessException unavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"MEDIA_PROVIDER_UNAVAILABLE","媒资服务暂不可用，请稍后重试");
    }
    static BusinessException conflict(String code,String message) { return new BusinessException(HttpStatus.CONFLICT,code,message); }
    static final class Invalid extends RuntimeException {
        final String code;
        Invalid(String code) { super("Invalid media"); this.code=code; }
    }
}
