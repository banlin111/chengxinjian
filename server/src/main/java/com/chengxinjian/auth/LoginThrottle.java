package com.chengxinjian.auth;

import com.chengxinjian.common.BusinessException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginThrottle {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public LoginThrottle(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    // 独立事务：登录失败时仍保留计数。仅保存摘要，不存原始账号或 IP。
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attempt(String username, String ip) {
        consume(TokenSecrets.hash("ip:" + ip), 100);
        consume(TokenSecrets.hash("account:" + username + ":" + ip), 10);
    }
    private void consume(String key, int limit) {
        var now = clock.instant();
        // Acquire an exclusive existing-row lock directly; duplicate INSERT + lock upgrade
        // deadlocks under concurrent login traffic on MySQL.
        jdbc.update("""
            INSERT INTO login_throttle(throttle_key,failed_count,window_started_at) VALUES (?,0,?)
            ON DUPLICATE KEY UPDATE throttle_key=throttle_key
            """,key,Timestamp.from(now));
        var row = jdbc.queryForMap("SELECT failed_count,window_started_at FROM login_throttle WHERE throttle_key = ? FOR UPDATE", key);
        var start = ((Timestamp) row.get("window_started_at")).toInstant();
        boolean expired = Duration.between(start, now).toSeconds() >= 60;
        int count = expired ? 0 : ((Number) row.get("failed_count")).intValue();
        if (count >= limit) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁，请稍后重试");
        }
        jdbc.update("UPDATE login_throttle SET failed_count = ?, window_started_at = ? WHERE throttle_key = ?",
            count + 1, Timestamp.from(expired ? now : start), key);
    }
}
