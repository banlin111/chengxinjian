package com.chengxinjian.common;

import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;

public record ApiResponse<T>(String code, String message, T data, String requestId,
                             Instant serverTime, @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) List<FieldError> details) {
    public record FieldError(String field, String reason) {}
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data, MDC.get("requestId"), Instant.now(), null);
    }
    public static ApiResponse<Void> error(String code, String message, List<FieldError> details) {
        return new ApiResponse<>(code, message, null, MDC.get("requestId"), Instant.now(), details);
    }
}
