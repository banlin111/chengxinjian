package com.chengxinjian.media;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;

public final class MediaModels {
    private MediaModels() {}
    public record Upload(@NotNull @Pattern(regexp="avatar|cover|course_media|meditation_media|noise_media|practice_recording") String purpose,
        @NotBlank @Size(max=255) String filename, @NotBlank @Size(max=100) String mimeType,
        @NotNull @Positive Long sizeBytes, @NotNull @Pattern(regexp="[0-9a-f]{64}") String sha256) {}
    public record Complete(@NotNull @Pattern(regexp="[0-9a-f]{64}") String sha256) {}
    public record Ticket(String assetId, String uploadUrl, String method, Map<String,String> formData,
        String fileField, Instant expiresAt, long maxSizeBytes) {
        @Override public String toString() { return "Ticket[private]"; }
    }
    public record Asset(String id, String purpose, String filename, String mimeType, long sizeBytes,
        Integer durationSeconds, String status, String publicUrl, int version, Instant createdAt, Instant updatedAt) {}
    public record Preview(String url, Instant expiresAt) {
        @Override public String toString() { return "Preview[private]"; }
    }
}
