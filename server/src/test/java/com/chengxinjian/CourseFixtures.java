package com.chengxinjian;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

final class CourseFixtures {
    private CourseFixtures() {}
    static void clear(JdbcTemplate jdbc) {
        for (String table : List.of("recording_reward_claim","recording_reward_rule","practice_submission","practice_checkin_request","practice_checkin","practice_event_request","practice_event",
                "practice_credit","practice_played_interval","playback_ticket_snapshot","lesson_playback_event","lesson_playback",
                "practice_audio_track","practice_session","mix_preset","practice_audio","user_content_relation","lesson_progress",
                "lesson_trial","content_reference_history","admin_grant","entitlement_grant","membership_plan","content_lesson","content_course","editorial_image"))
            jdbc.update("DELETE FROM "+table);
    }
}
