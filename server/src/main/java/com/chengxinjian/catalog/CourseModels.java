package com.chengxinjian.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class CourseModels {
    private CourseModels() {}
    public static final String DIFFICULTY = "beginner|intermediate|advanced";
    public record CourseInput(@NotBlank @Size(max=200) String title, @NotNull @Size(max=1000) String summary,
        @NotNull @Size(max=10000) String description, @NotBlank @Size(max=36) String coverAssetId,
        @NotBlank @Size(max=36) String categoryId, @NotBlank @Size(max=36) String teacherId,
        @NotNull @Pattern(regexp=DIFFICULTY) String difficulty, @NotNull @PositiveOrZero Integer priceFen,
        @JsonProperty(required=true) @PositiveOrZero Integer originalPriceFen,
        @NotNull @Pattern(regexp="free|member|purchase|member_or_purchase") String accessPolicy,
        @NotNull @Pattern(regexp=CategoryModels.STATUS) String status) {}
    public record CourseUpdate(@NotBlank @Size(max=200) String title, @NotNull @Size(max=1000) String summary,
        @NotNull @Size(max=10000) String description, @NotBlank @Size(max=36) String coverAssetId,
        @NotBlank @Size(max=36) String categoryId, @NotBlank @Size(max=36) String teacherId,
        @NotNull @Pattern(regexp=DIFFICULTY) String difficulty, @NotNull @PositiveOrZero Integer priceFen,
        @JsonProperty(required=true) @PositiveOrZero Integer originalPriceFen,
        @NotNull @Pattern(regexp="free|member|purchase|member_or_purchase") String accessPolicy,
        @NotNull @Pattern(regexp=CategoryModels.STATUS) String status, @NotNull @Positive Integer version) {
        CourseInput input() {
            return new CourseInput(title,summary,description,coverAssetId,categoryId,teacherId,difficulty,priceFen,originalPriceFen,accessPolicy,status);
        }
    }
    public record LessonInput(@NotBlank @Size(max=36) String courseId, @NotBlank @Size(max=200) String title,
        @NotNull Integer sort, @NotBlank @Size(max=36) String assetId, @NotNull @PositiveOrZero Integer trialSeconds,
        @NotNull @Pattern(regexp=CategoryModels.STATUS) String status) {}
    public record LessonUpdate(@NotBlank @Size(max=36) String courseId, @NotBlank @Size(max=200) String title,
        @NotNull Integer sort, @NotBlank @Size(max=36) String assetId, @NotNull @PositiveOrZero Integer trialSeconds,
        @NotNull @Pattern(regexp=CategoryModels.STATUS) String status, @NotNull @Positive Integer version) {
        LessonInput input() { return new LessonInput(courseId,title,sort,assetId,trialSeconds,status); }
    }
    public record Access(boolean allowed, String reason, int trialSeconds, Instant expiresAt) {}
    public record Filters(@Size(max=36) String categoryId, @Size(max=36) String teacherId,
        @Pattern(regexp=DIFFICULTY) String difficulty, @PositiveOrZero Integer minSeconds, @PositiveOrZero Integer maxSeconds,
        Boolean memberOnly, @Pattern(regexp="recommended|newest|duration_asc") String sort) {}
    public record AdminFilters(@Size(max=100) String q, @Pattern(regexp=CategoryModels.STATUS) String status,
        @Size(max=36) String categoryId, @Size(max=36) String courseId, @Size(max=36) String teacherId) {}
}
