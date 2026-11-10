package com.chengxinjian.common;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<?> business(BusinessException e) {
        var response = ResponseEntity.status(e.status());
        if (e.status() == HttpStatus.TOO_MANY_REQUESTS) { response.header("Retry-After", "60"); }
        return response.body(ApiResponse.error(e.code(), e.getMessage(), null));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        var details = e.getBindingResult().getFieldErrors().stream()
            .map(f -> new ApiResponse.FieldError(f.getField(), f.getDefaultMessage())).toList();
        return badRequest(details);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class,
        ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class})
    ResponseEntity<?> malformed(Exception e) { return badRequest(List.of()); }
    private ResponseEntity<?> badRequest(List<ApiResponse.FieldError> details) {
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_FAILED", "请求参数不正确", details));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> forbidden(AccessDeniedException e) { return business(BusinessException.forbidden()); }
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<?> missing(Exception e) { return business(BusinessException.notFound()); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> method(Exception e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.error("METHOD_NOT_ALLOWED", "不支持此请求方法", null));
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<?> mediaType(Exception e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiResponse.error("UNSUPPORTED_MEDIA_TYPE", "请使用 JSON 请求体", null));
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> database(DataAccessException e) {
        // SQL 异常可能包含手机号、密钥或请求数据，禁止原样写入日志和响应。
        log.error("Database operation failed: {}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error("PROVIDER_UNAVAILABLE", "服务暂不可用，请稍后重试", null));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) {
        log.error("Unexpected failure: {}", e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(ApiResponse.error("INTERNAL_ERROR", "服务处理失败", null));
    }
}
