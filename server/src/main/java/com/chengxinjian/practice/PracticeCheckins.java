package com.chengxinjian.practice;

import com.chengxinjian.common.*;
import com.chengxinjian.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PracticeCheckins {
    record Request(@NotBlank @Size(max=36) String practiceSessionId) {}
    record Checkin(LocalDate date, int streakDays, int creditedSeconds, boolean alreadyCheckedIn, List<?> rewards) {}
    public record Current(int streakDays, boolean checkedInToday) {}
    record Calendar(List<LocalDate> dates, int streakDays, boolean checkedInToday) {}

    private final JdbcTemplate jdbc;
    private final UserService users;
    private final Clock clock;
    private final int threshold;
    private final String ruleVersion;

    PracticeCheckins(JdbcTemplate jdbc, UserService users, Clock clock,
                     @Value("${app.practice.checkin-seconds:0}") int threshold,
                     @Value("${app.practice.rule-version:}") String ruleVersion) {
        if (threshold < 0 || ruleVersion.length() > 80 || (threshold > 0) == ruleVersion.isBlank())
            throw new IllegalArgumentException("Configure a positive checkin threshold and rule version together");
        this.jdbc = jdbc;
        this.users = users;
        this.clock = clock;
        this.threshold = threshold;
        this.ruleVersion = ruleVersion;
    }

    LocalDate today() { return LocalDate.ofInstant(clock.instant(), PracticeLedger.SHANGHAI); }

    private int seconds(String user, LocalDate day) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(end_sec-start_sec),0) FROM practice_credit WHERE user_id=? AND business_day=?
            """, Integer.class, user, day);
    }

    // Eligibility is the user's daily total across devices. The POST references an owned session.
    boolean eligible(String user) { return threshold > 0 && seconds(user, today()) >= threshold; }

    private int streak(String user, LocalDate day) {
        return jdbc.queryForList("SELECT streak_days FROM practice_checkin WHERE user_id=? AND business_day=?",
            Integer.class, user, day).stream().findFirst().orElse(0);
    }

    public Current current(String user) {
        var day = today();
        int checked = streak(user, day);
        return new Current(checked > 0 ? checked : streak(user, day.minusDays(1)), checked > 0);
    }

    int through(String user, LocalDate to) {
        return to.isBefore(today()) ? streak(user, to) : current(user).streakDays();
    }

    private Checkin receipt(String user, LocalDate day, boolean already) {
        return jdbc.queryForObject("SELECT streak_days,credited_seconds FROM practice_checkin WHERE user_id=? AND business_day=?",
            (r,n) -> new Checkin(day, r.getInt(1), r.getInt(2), already, List.of()), user, day);
    }

    @PostMapping("/checkins")
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ApiResponse<Checkin> checkin(@AuthenticationPrincipal UserPrincipal user,
            @RequestHeader("Idempotency-Key") String requestKey, @RequestBody @Valid Request body) {
        String key = PracticeSessions.key(requestKey);
        users.requireActive(user.id(), true);
        String session = body.practiceSessionId();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM practice_session WHERE id=? AND user_id=?", Integer.class, session, user.id()) == 0)
            throw BusinessException.notFound();
        var saved = jdbc.query("""
            SELECT session_id,business_day,was_checked_in FROM practice_checkin_request WHERE user_id=? AND request_key=?
            """, (r,n) -> {
                if (!session.equals(r.getString(1)))
                    throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "打卡请求标识已用于其他练习");
                return receipt(user.id(), r.getObject(2,LocalDate.class), r.getBoolean(3));
            }, user.id(), key);
        if (!saved.isEmpty()) return ApiResponse.ok(saved.getFirst());
        var day = today();
        boolean already = streak(user.id(), day) > 0;
        if (!already) {
            if (threshold == 0)
                throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "PRACTICE_RULE_UNCONFIGURED", "打卡规则待确认");
            int seconds = seconds(user.id(), day);
            if (seconds < threshold)
                throw new BusinessException(HttpStatus.CONFLICT, "CHECKIN_NOT_ELIGIBLE", "当天有效练习尚未达到打卡时长");
            jdbc.update("""
                INSERT INTO practice_checkin(user_id,business_day,session_id,credited_seconds,streak_days,rule_version,threshold_seconds)
                VALUES (?,?,?,?,?,?,?)
                """, user.id(), day, session, seconds, streak(user.id(), day.minusDays(1)) + 1, ruleVersion, threshold);
        }
        jdbc.update("""
            INSERT INTO practice_checkin_request(user_id,request_key,session_id,business_day,was_checked_in) VALUES (?,?,?,?,?)
            """, user.id(), key, session, day, already);
        return ApiResponse.ok(receipt(user.id(), day, already));
    }

    @GetMapping("/me/checkins")
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public ApiResponse<Calendar> calendar(@AuthenticationPrincipal UserPrincipal user, @RequestParam String month) {
        YearMonth ym;
        try { ym = YearMonth.parse(month); }
        catch (DateTimeException ex) { throw BusinessException.invalid("月份格式应为 YYYY-MM"); }
        if (ym.getYear() < 1000 || ym.getYear() > 9999) throw BusinessException.invalid("月份范围无效");
        var dates = jdbc.queryForList("""
            SELECT business_day FROM practice_checkin WHERE user_id=? AND business_day BETWEEN ? AND ? ORDER BY business_day
            """, LocalDate.class, user.id(), ym.atDay(1), ym.atEndOfMonth());
        var status = current(user.id());
        return ApiResponse.ok(new Calendar(dates, status.streakDays(), status.checkedInToday()));
    }
}
