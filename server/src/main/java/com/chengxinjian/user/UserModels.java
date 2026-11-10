package com.chengxinjian.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class UserModels {
    private UserModels() {}
    public enum AgreementType { privacy, user, membership }
    public enum Theme { paper, turquoise, forest }
    public record AgreementRef(@NotNull AgreementType type, @NotBlank @Size(max=64) String version) {}
    public record Agreement(AgreementType type, String version, String title, String content, Instant effectiveAt) {}
    public record WxLogin(@NotBlank @Size(max=256) String code, @NotNull @Size(max=3) List<@NotNull @Valid AgreementRef> agreements) {}
    public record Membership(boolean active, String planId, String planName, Instant startsAt, Instant expiresAt, boolean lifetime) {}
    public record User(String id, String nickname, String avatarUrl, String phoneMasked, boolean hasPassword,
                       List<String> roles, String status, Membership membership, Theme theme) {}
    public record LoginResult(String accessToken, String refreshToken, long expiresIn, long refreshExpiresIn,
                              String tokenType, User user, boolean isNewUser, boolean phoneBindingRequired) {}
    public record Settings(@NotNull Theme theme, @NotNull Boolean backgroundAudio, @NotNull Double defaultSpeed,
        @NotNull Boolean autoNext, @Size(max=36) String endBellAssetId, @NotNull Boolean practiceReminderEnabled,
        @Pattern(regexp="(?:[01][0-9]|2[0-3]):[0-5][0-9]") String practiceReminderTime,
        @NotNull Boolean liveReminderEnabled, @NotNull Boolean membershipReminderEnabled, @Min(1) Integer version) {
        public Settings withVersion(int v) { return new Settings(theme, backgroundAudio, defaultSpeed, autoNext, endBellAssetId,
            practiceReminderEnabled, practiceReminderTime, liveReminderEnabled, membershipReminderEnabled, v); }
        public static Settings defaults() { return new Settings(Theme.paper, true, 1.0, true, null, false, null, false, false, 1); }
    }
}
