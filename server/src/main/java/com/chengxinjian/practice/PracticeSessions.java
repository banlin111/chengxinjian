package com.chengxinjian.practice;

import com.chengxinjian.auth.TokenSecrets;
import com.chengxinjian.catalog.CategoryModels.Page;
import com.chengxinjian.common.*;
import com.chengxinjian.user.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
@Transactional(isolation=Isolation.READ_COMMITTED)
public class PracticeSessions {
    private static final String BASE = "/practice/sessions";
    private static final int HEARTBEAT_SECONDS = 20;
    record Track(@NotBlank String noiseId, @NotNull Double volume) {}
    record Start(@NotNull @Pattern(regexp="timer|guided|noise") String kind, @NotNull @Positive Integer plannedSeconds,
                 @NotNull List<@NotNull @Valid Track> tracks, String meditationId, String mixPresetId, String endBellAssetId) {}
    record Event(@NotBlank @Size(max=36) String eventId, @NotNull @Positive Integer sequence,
                 @NotNull @Pattern(regexp="heartbeat|pause|resume|finish|abandon") String action,
                 @NotNull @PositiveOrZero Integer activeDeltaSeconds, @NotNull Instant occurredAt) {}
    record Practice(String id, int version, Instant createdAt, Instant updatedAt, String kind, String status,
                    Instant startedAt, Instant endedAt, int plannedSeconds, int creditedSeconds,
                    boolean checkinEligible, int heartbeatSeconds) {}
    record Stats(LocalDate from, LocalDate to, long totalSeconds, long completedSessions,
                 int streakDays, int checkinDays, List<PracticeLedger.Day> daily) {}

    private final JdbcTemplate jdbc;
    private final UserService users;
    private final PracticeLedger ledger;
    private final ObjectMapper json;
    private final Clock clock;
    private final int minTimer, maxTimer, eventCap, backlog;
    private final PracticeCheckins checkins;

    PracticeSessions(JdbcTemplate jdbc, UserService users, PracticeLedger ledger, ObjectMapper json, Clock clock, PracticeCheckins checkins,
                     @Value("${app.practice.min-timer-seconds:1}") int minTimer,
                     @Value("${app.practice.max-timer-seconds:2147483647}") int maxTimer,
                     @Value("${app.practice.max-event-seconds:60}") int eventCap,
                     @Value("${app.practice.backfill-seconds:300}") int backlog) {
        if (minTimer < 1 || maxTimer < minTimer || eventCap < 1 || backlog < eventCap)
            throw new IllegalArgumentException("Invalid practice timing configuration");
        this.jdbc = jdbc;
        this.users = users;
        this.ledger = ledger;
        this.json = json;
        this.clock = clock;
        this.minTimer = minTimer;
        this.maxTimer = maxTimer;
        this.eventCap = eventCap;
        this.backlog = backlog;
        this.checkins = checkins;
    }

    static String key(String value) {
        try {
            String normalized = UUID.fromString(value).toString();
            if (normalized.equalsIgnoreCase(value)) return normalized;
        } catch (IllegalArgumentException ignored) {}
        throw BusinessException.invalid("Idempotency-Key 必须为 UUID");
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (java.io.IOException ex) { throw new IllegalStateException("Invalid practice snapshot", ex); }
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }

    private Practice replay(Map<String,Object> record, String hash) {
        if (!hash.equals(record.get("request_hash")))
            throw conflict("IDEMPOTENCY_CONFLICT", "相同的标识或序号已用于其他请求");
        try { return json.readValue((String) record.get("snapshot"), Practice.class); }
        catch (java.io.IOException ex) { throw new IllegalStateException("Invalid stored practice snapshot", ex); }
    }

    @PostMapping(BASE)
    public ApiResponse<Practice> create(@AuthenticationPrincipal UserPrincipal user,
            @RequestHeader("Idempotency-Key") String requestKey, @Valid @RequestBody Start input) {
        String key = key(requestKey);
        users.requireActive(user.id(), true);
        String hash = TokenSecrets.hash(encode(input));
        var saved = jdbc.queryForList("""
            SELECT request_hash,create_snapshot AS snapshot FROM practice_session WHERE user_id=? AND create_key=?
            """, user.id(), key);
        if (!saved.isEmpty()) return ApiResponse.ok(replay(saved.getFirst(), hash));
        if (!input.kind().equals("timer") || !input.tracks().isEmpty() || input.meditationId()!=null
                || input.mixPresetId()!=null || input.endBellAssetId()!=null)
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "FEATURE_UNAVAILABLE", "当前支持无音轨和提示音的纯定时练习");
        if (input.plannedSeconds() < minTimer || input.plannedSeconds() > maxTimer)
            throw BusinessException.invalid("计划时长超出配置范围");
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var practice = new Practice(UUID.randomUUID().toString(), 1, now, now, "timer", "active", now, null,
            input.plannedSeconds(), 0, checkins.eligible(user.id()), HEARTBEAT_SECONDS);
        jdbc.update("""
            INSERT INTO practice_session(id,user_id,create_key,request_hash,create_snapshot,status,planned_seconds,started_sec,last_event_sec,updated_sec)
            VALUES (?,?,?,?,?,'active',?,?,?,?)
            """, practice.id(), user.id(), key, hash, encode(practice), input.plannedSeconds(), now.getEpochSecond(),
            now.getEpochSecond(), now.getEpochSecond());
        return ApiResponse.ok(practice);
    }

    private Practice practice(ResultSet r, boolean eligible) throws SQLException {
        Instant start = Instant.ofEpochSecond(r.getLong("started_sec"));
        Long end = r.getObject("ended_sec", Long.class);
        return new Practice(r.getString("id"), r.getInt("version"), start, Instant.ofEpochSecond(r.getLong("updated_sec")),
            "timer", r.getString("status"), start, end == null ? null : Instant.ofEpochSecond(end), r.getInt("planned_seconds"),
            r.getInt("credited_seconds"), eligible, HEARTBEAT_SECONDS);
    }

    private Practice find(String user, String id) {
        boolean eligible = checkins.eligible(user);
        return jdbc.query("SELECT * FROM practice_session WHERE user_id=? AND id=?", (r,n) -> practice(r, eligible), user, id)
            .stream().findFirst().orElseThrow(BusinessException::notFound);
    }

    @GetMapping(BASE + "/{id}")
    public ApiResponse<Practice> get(@AuthenticationPrincipal UserPrincipal user, @PathVariable String id) {
        return ApiResponse.ok(find(user.id(), id));
    }

    private static String nextStatus(String status, String action) {
        if (status.equals("active") || status.equals("paused")) {
            if (action.equals("finish")) return "completed";
            if (action.equals("abandon")) return "abandoned";
            if (status.equals("active") && action.equals("heartbeat")) return "active";
            if (status.equals("active") && action.equals("pause")) return "paused";
            if (status.equals("paused") && action.equals("resume")) return "active";
        }
        throw conflict("PRACTICE_STATE_CONFLICT", "练习状态已变更，请重新读取");
    }

    @PostMapping(BASE + "/{id}/events")
    public ApiResponse<Practice> event(@AuthenticationPrincipal UserPrincipal user, @PathVariable String id,
            @RequestHeader("Idempotency-Key") String requestKey, @Valid @RequestBody Event input) {
        String key = key(requestKey);
        users.requireActive(user.id(), true);
        var session = find(user.id(), id);
        String hash = TokenSecrets.hash(encode(input));
        var receipts = jdbc.queryForList("""
            SELECT e.request_hash,e.snapshot FROM practice_event e WHERE e.session_id=? AND
            (e.sequence_no=? OR e.event_id=? OR e.sequence_no=(
                SELECT sequence_no FROM practice_event_request WHERE session_id=? AND request_key=?))
            """, id, input.sequence(), input.eventId(), id, key);
        if (receipts.size() > 1) throw conflict("IDEMPOTENCY_CONFLICT", "事件标识对应多个请求");
        if (!receipts.isEmpty()) {
            var snapshot = replay(receipts.getFirst(), hash);
            jdbc.update("""
                INSERT INTO practice_event_request(session_id,request_key,sequence_no) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE sequence_no=?
                """, id, key, input.sequence(), input.sequence());
            return ApiResponse.ok(snapshot);
        }
        // Creation is version 1. Every accepted event advances exactly once, so GET also gives the next sequence.
        if (input.sequence() != session.version())
            throw conflict("PRACTICE_SEQUENCE_CONFLICT", "先按序补传，或重新读取练习版本");
        String status = nextStatus(session.status(), input.action());
        long now = clock.instant().getEpochSecond();
        long previous = jdbc.queryForObject("SELECT last_event_sec FROM practice_session WHERE id=?", Long.class, id);
        long reported = input.occurredAt().getEpochSecond();
        if (reported > now + 30 || reported < previous || now < previous)
            throw BusinessException.invalid("事件时间超出会话顺序或服务端时间");
        long end = Math.min(reported, now);
        int credited = session.creditedSeconds();
        if (session.status().equals("active")) {
            long start = Math.max(Math.max(previous, now - backlog), end - Math.min(input.activeDeltaSeconds(), eventCap));
            credited += ledger.credit(user.id(), id, start, end, session.plannedSeconds() - credited);
        }
        Long ended = status.equals("completed") || status.equals("abandoned") ? end : null;
        jdbc.update("""
            UPDATE practice_session SET version=version+1,status=?,credited_seconds=?,last_event_sec=?,updated_sec=?,ended_sec=? WHERE id=?
            """, status, credited, end, now, ended, id);
        var result = find(user.id(), id);
        jdbc.update("""
            INSERT INTO practice_event(session_id,sequence_no,event_id,action,occurred_sec,request_hash,snapshot)
            VALUES (?,?,?,?,?,?,?)
            """, id, input.sequence(), input.eventId(), input.action(), end, hash, encode(result));
        jdbc.update("INSERT INTO practice_event_request(session_id,request_key,sequence_no) VALUES (?,?,?)",
            id, key, input.sequence());
        return ApiResponse.ok(result);
    }

    private static void dates(LocalDate from, LocalDate to) {
        if (from != null && (from.getYear() < 1000 || from.getYear() > 9999)
                || to != null && (to.getYear() < 1000 || to.getYear() > 9999)
                || from != null && to != null && from.isAfter(to))
            throw BusinessException.invalid("日期范围无效");
    }

    @GetMapping("/me/practice/sessions")
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public ApiResponse<Page<Practice>> history(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate to,
            @RequestParam(defaultValue="1") @Min(1) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        dates(from, to);
        var args = new ArrayList<Object>();
        String filter = " FROM practice_session WHERE user_id=?";
        args.add(user.id());
        if (from != null) { filter += " AND started_sec>=?"; args.add(PracticeLedger.midnight(from)); }
        if (to != null) { filter += " AND started_sec<?"; args.add(PracticeLedger.midnight(to.plusDays(1))); }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + filter, Long.class, args.toArray());
        args.add(pageSize);
        args.add(((long) page - 1) * pageSize);
        boolean eligible = checkins.eligible(user.id());
        var items = jdbc.query("SELECT *" + filter + " ORDER BY started_sec DESC,id DESC LIMIT ? OFFSET ?",
            (r,n) -> practice(r, eligible), args.toArray());
        return ApiResponse.ok(new Page<>(items, page, pageSize, total));
    }

    @GetMapping("/me/practice/stats")
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public ApiResponse<Stats> stats(@AuthenticationPrincipal UserPrincipal user, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        dates(from, to);
        if (ChronoUnit.DAYS.between(from, to) >= 366) throw BusinessException.invalid("每次最多查询366个自然日");
        var daily = ledger.days(user.id(), from, to);
        long completed = jdbc.queryForObject("""
            SELECT COUNT(*) FROM practice_session WHERE user_id=? AND status='completed' AND ended_sec>=? AND ended_sec<?
            """, Long.class, user.id(), PracticeLedger.midnight(from), PracticeLedger.midnight(to.plusDays(1)));
        return ApiResponse.ok(new Stats(from, to, daily.stream().mapToLong(PracticeLedger.Day::seconds).sum(), completed,
            checkins.through(user.id(), to), (int) daily.stream().filter(PracticeLedger.Day::checkedIn).count(), daily));
    }
}
