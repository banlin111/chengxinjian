package com.chengxinjian;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

final class CourseFixtures {
    private CourseFixtures() {}
    static void clear(JdbcTemplate jdbc) {
        for (String table : List.of("user_content_relation","playback_ticket_snapshot","lesson_playback_event","lesson_playback","lesson_progress",
                "lesson_trial","content_reference_history","admin_grant","entitlement_grant","membership_plan","content_lesson","content_course","editorial_image"))
            jdbc.update("DELETE FROM "+table);
    }
}
