package com.chengxinjian.practice;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PracticeLedger {
    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;

    PracticeLedger(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    record Interval(long start, long end) {}
    record Day(LocalDate date, long seconds, boolean checkedIn) {}

    static long midnight(LocalDate day) { return day.atStartOfDay(SHANGHAI).toEpochSecond(); }

    private static LocalDate dayOf(long second) { return LocalDate.ofInstant(Instant.ofEpochSecond(second), SHANGHAI); }

    // The caller holds the app_user row lock. Earlier accepted intervals keep their attribution.
    int credit(String user, String session, long start, long end, int remaining) {
        if (end <= start || remaining == 0) return 0;
        var overlaps = jdbc.query("""
            SELECT start_sec,end_sec FROM practice_credit
            WHERE user_id=? AND business_day BETWEEN ? AND ? AND start_sec<? AND end_sec>? ORDER BY start_sec
            """, (r,n) -> new Interval(r.getLong(1), r.getLong(2)), user, dayOf(start), dayOf(end - 1), end, start);
        int credited = 0;
        for (var overlap : overlaps) {
            credited += insertGap(user, session, start, overlap.start(), remaining - credited);
            if (credited == remaining) return credited;
            start = Math.max(start, overlap.end());
        }
        return credited + insertGap(user, session, start, end, remaining - credited);
    }

    boolean hasPlayed(String session,long start,long end) {
        return !jdbc.queryForList("SELECT 1 FROM practice_played_interval WHERE session_id=? AND start_sec<? AND end_sec>? LIMIT 1",
            Integer.class,session,end,start).isEmpty();
    }

    // Called under the same owner lock as practice events. Whichever stream arrives second settles the intersection.
    public void played(String user,String session,String audio,String playback,int sequence,long start,long end,long now) {
        int inserted=jdbc.update("""
            INSERT INTO practice_played_interval(playback_id,sequence_no,session_id,start_sec,end_sec)
            SELECT ?,?,?,?,? FROM practice_audio_track t JOIN practice_session s ON s.id=t.session_id
            WHERE t.session_id=? AND t.audio_id=? AND s.user_id=? AND s.status IN ('active','paused')
                AND (t.track_no=-1 OR (s.kind='noise' AND t.volume>0))
            """,playback,sequence,session,start,end,session,audio,user);
        if (inserted==0) return;
        int remaining=jdbc.queryForObject("SELECT planned_seconds-credited_seconds FROM practice_session WHERE id=?",Integer.class,session);
        var claims=jdbc.query("""
            SELECT claim_start_sec,claim_end_sec FROM practice_event
            WHERE session_id=? AND claim_start_sec<? AND claim_end_sec>? ORDER BY claim_start_sec
            """,(r,n)->new Interval(r.getLong(1),r.getLong(2)),session,end,start);
        int added=0;
        for (var claim:claims) {
            added+=credit(user,session,Math.max(start,claim.start()),Math.min(end,claim.end()),remaining-added);
            if (added==remaining) break;
        }
        if (added>0) jdbc.update("UPDATE practice_session SET credited_seconds=credited_seconds+?,updated_sec=GREATEST(updated_sec,?) WHERE id=?",added,now,session);
    }

    int creditPlayed(String user,String session,long start,long end,int remaining) {
        if (end<=start || remaining==0) return 0;
        var played=jdbc.query("""
            SELECT start_sec,end_sec FROM practice_played_interval
            WHERE session_id=? AND start_sec<? AND end_sec>? ORDER BY start_sec,end_sec
            """,(r,n)->new Interval(r.getLong(1),r.getLong(2)),session,end,start);
        int added=0;
        for (var interval:played) {
            long until=Math.min(end,interval.end());
            added+=credit(user,session,Math.max(start,interval.start()),until,remaining-added);
            start=Math.max(start,until);
            if (start>=end || added==remaining) break;
        }
        return added;
    }

    private int insertGap(String user, String session, long start, long end, int remaining) {
        end = Math.min(end, start + remaining);
        int added = 0;
        while (start < end) {
            var day = dayOf(start);
            long until = Math.min(end, midnight(day.plusDays(1)));
            jdbc.update("INSERT INTO practice_credit(user_id,session_id,business_day,start_sec,end_sec) VALUES (?,?,?,?,?)",
                user, session, day, start, until);
            added += Math.toIntExact(until - start);
            start = until;
        }
        return added;
    }

    List<Day> days(String user, LocalDate from, LocalDate to) {
        Map<LocalDate, Long> totals = new HashMap<>();
        jdbc.query("""
            SELECT business_day,SUM(end_sec-start_sec) FROM practice_credit
            WHERE user_id=? AND business_day BETWEEN ? AND ? GROUP BY business_day
            """, r -> { totals.put(r.getObject(1, LocalDate.class), r.getLong(2)); }, user, from, to);
        var checked = new HashSet<>(jdbc.queryForList("""
            SELECT business_day FROM practice_checkin WHERE user_id=? AND business_day BETWEEN ? AND ?
            """, LocalDate.class, user, from, to));
        return from.datesUntil(to.plusDays(1)).map(d -> new Day(d, totals.getOrDefault(d, 0L), checked.contains(d))).toList();
    }
}
