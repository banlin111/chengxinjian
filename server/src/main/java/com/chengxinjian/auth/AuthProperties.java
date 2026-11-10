package com.chengxinjian.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(Duration accessTtl, Duration refreshTtl, String captchaMode,
    String localCaptchaTicket, String bootstrapUsername, String bootstrapPassword, String allowedOrigins) {}
