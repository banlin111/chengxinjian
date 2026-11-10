package com.chengxinjian.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;

@Validated
@ConfigurationProperties("app.media")
public record MediaProperties(String provider, String bucket, String region, String secretId, String secretKey,
    String ffmpegPath, @Min(1) @Max(2147483648L) long maxMediaBytes, @Min(1) @Max(86400) int maxDurationSeconds,
    @Min(1) @Max(300) int decodeTimeoutSeconds, boolean workerEnabled) {
    // Deliberately omit credentials from the record's generated representation.
    @Override public String toString() { return "MediaProperties[private]"; }
}
