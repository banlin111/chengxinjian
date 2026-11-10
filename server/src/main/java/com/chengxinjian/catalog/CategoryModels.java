package com.chengxinjian.catalog;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class CategoryModels {
    private CategoryModels() {}
    public static final String KIND = "course|meditation|noise";
    public static final String STATUS = "draft|published|archived";
    public record Category(String id, String name, String kind, int sort, String status,
                           int version, Instant createdAt, Instant updatedAt) {}
    public record Create(@NotBlank @Size(max=64) String name, @NotNull @Pattern(regexp=KIND) String kind,
                         @NotNull Integer sort, @NotNull @Pattern(regexp=STATUS) String status) {}
    public record Update(@NotBlank @Size(max=64) String name, @NotNull @Pattern(regexp=KIND) String kind,
                         @NotNull Integer sort, @NotNull @Pattern(regexp=STATUS) String status,
                         @NotNull @Min(1) Integer version) {}
    public record Archive(@NotBlank @Size(max=500) String reason, @NotNull @Min(1) Integer version) {}
    public record Page<T>(List<T> items, int page, int pageSize, long total) {}
}
