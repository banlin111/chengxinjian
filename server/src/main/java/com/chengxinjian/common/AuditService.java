package com.chengxinjian.common;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public AuditService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    public void record(String operator, String action, String target, String reason) {
        jdbc.update("INSERT INTO audit_log(id,operator_id,action,target_id,reason,request_id,occurred_at) VALUES (?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(), operator, action, target, reason,
            MDC.get("requestId") == null ? "system" : MDC.get("requestId"), Timestamp.from(clock.instant()));
    }
}
