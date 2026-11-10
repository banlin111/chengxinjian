package com.chengxinjian.common;

import com.chengxinjian.auth.TokenSecrets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateSnapshot {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    public CreateSnapshot(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public JsonNode admin(String actor, String operation, String key, Object input,
                          Consumer<String> authorizeReplay, Supplier<JsonNode> create) {
        try {
            if (!UUID.fromString(key).toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw BusinessException.invalid("Idempotency-Key 必须为 UUID");
        }
        jdbc.queryForObject("SELECT id FROM admin_account WHERE id=? FOR UPDATE", String.class, actor);
        String hash = TokenSecrets.hash(json.valueToTree(input).toString());
        var records = jdbc.queryForList("SELECT request_hash,result_id,result_json FROM idempotency_record WHERE actor_id=? AND operation=? AND idempotency_key=?",
            actor, operation, key);
        if (!records.isEmpty()) {
            var record = records.getFirst();
            authorizeReplay.accept((String)record.get("result_id"));
            if (!hash.equals(record.get("request_hash"))) {
                throw new BusinessException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","相同幂等标识不能用于不同请求");
            }
            try { return json.readTree((String)record.get("result_json")); }
            catch (java.io.IOException ex) { throw new IllegalStateException("Invalid stored snapshot", ex); }
        }
        var result = create.get();
        jdbc.update("INSERT INTO idempotency_record(actor_id,operation,idempotency_key,request_hash,result_id,result_json,created_at) VALUES (?,?,?,?,?,?,?)",
            actor, operation, key, hash, result.get("id").asText(), result.toString(), Timestamp.from(clock.instant()));
        return result;
    }
}
