package com.chengxinjian.catalog;

import com.chengxinjian.common.ApiResponse;
import com.chengxinjian.user.UserPrincipal;
import com.chengxinjian.practice.PracticeCheckins;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    private final CourseCatalog catalog;
    private final LearningHistory history;
    private final Notices notices;
    private final PracticeCheckins checkins;

    HomeController(CourseCatalog catalog, LearningHistory history, Notices notices, PracticeCheckins checkins) {
        this.catalog = catalog;
        this.history = history;
        this.notices = notices;
        this.checkins = checkins;
    }

    record Home(String greeting, ObjectNode continueLearning, List<ObjectNode> recommendedCourses,
                List<ObjectNode> recommendedMeditations, List<ObjectNode> newCourses, List<Notices.Notice> notices,
                List<ObjectNode> banners, boolean checkedInToday, int streakDays) {}

    @GetMapping("/api/v1/home")
    @Transactional(readOnly = true)
    public ApiResponse<Home> home(@AuthenticationPrincipal UserPrincipal user) {
        // The mobile homepage shows one featured card followed by a separate row of new courses.
        var courses = catalog.newestVisible(user, 7);
        int featured = Math.min(1, courses.size());
        var resume = user == null ? null : history.latestUnfinished(user.id());
        var checkin = user == null ? new PracticeCheckins.Current(0, false) : checkins.current(user.id());
        return ApiResponse.ok(new Home("你好，涧友", resume, courses.subList(0, featured), List.of(),
            courses.subList(featured, courses.size()), notices.latest(user, 3), List.of(), checkin.checkedInToday(), checkin.streakDays()));
    }
}
