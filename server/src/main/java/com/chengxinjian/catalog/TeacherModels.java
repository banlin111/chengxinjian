package com.chengxinjian.catalog;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class TeacherModels {
    private TeacherModels() {}
    public record Input(@NotBlank @Size(max=64) String name, @com.fasterxml.jackson.annotation.JsonProperty(required=true) @Size(max=36) String avatarAssetId,
        @NotNull @Size(max=10000) String bio, @NotNull @Size(max=20) List<@NotBlank @Size(max=200) String> credentials,
        @com.fasterxml.jackson.annotation.JsonProperty(required=true) @Size(max=36) String userId, @NotNull @Pattern(regexp=CategoryModels.STATUS) String status) {}
    public record Update(@NotBlank @Size(max=64) String name, @com.fasterxml.jackson.annotation.JsonProperty(required=true) @Size(max=36) String avatarAssetId,
        @NotNull @Size(max=10000) String bio, @NotNull @Size(max=20) List<@NotBlank @Size(max=200) String> credentials,
        @com.fasterxml.jackson.annotation.JsonProperty(required=true) @Size(max=36) String userId, @NotNull @Pattern(regexp=CategoryModels.STATUS) String status,
        @NotNull @Min(1) Integer version) {
        Input input() { return new Input(name,avatarAssetId,bio,credentials,userId,status); }
    }
    public record Teacher(String id, String name, String avatarUrl, String bio, List<String> credentials,
        boolean followed, String status, int version, Instant createdAt, Instant updatedAt) {}
    public record AdminTeacher(String id, String name, String avatarUrl, String bio, List<String> credentials,
        boolean followed, String status, int version, Instant createdAt, Instant updatedAt, String avatarAssetId, String userId) {
        Teacher publicView(boolean followed) { return new Teacher(id,name,avatarUrl,bio,credentials,followed,status,version,createdAt,updatedAt); }
    }
    public record Follow(@NotNull Boolean followed) {}
}
