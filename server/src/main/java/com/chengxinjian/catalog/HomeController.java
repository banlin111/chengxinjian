package com.chengxinjian.catalog;

import com.chengxinjian.common.ApiResponse;
import com.chengxinjian.user.UserPrincipal;
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

    HomeController(CourseCatalog catalog, LearningHistory history) {
        this.catalog = catalog;
        this.history = history;
    }

    record Home(String greeting, ObjectNode continueLearning, List<ObjectNode> recommendedCourses,
                List<ObjectNode> recommendedMeditations, List<ObjectNode> newCourses, List<ObjectNode> notices,
                List<ObjectNode> banners, boolean checkedInToday, int streakDays) {}

    @GetMapping("/api/v1/home")
    @Transactional(readOnly = true)
    public ApiResponse<Home> home(@AuthenticationPrincipal UserPrincipal user) {
        // The mobile homepage shows one featured card followed by a separate row of new courses.
        var courses = catalog.newestVisible(user, 7);
        int featured = Math.min(1, courses.size());
        var resume = user == null ? null : history.latestUnfinished(user.id());
        return ApiResponse.ok(new Home("你好，涧友", resume, courses.subList(0, featured), List.of(),
            courses.subList(featured, courses.size()), List.of(), List.of(), false, 0));
    }
}
