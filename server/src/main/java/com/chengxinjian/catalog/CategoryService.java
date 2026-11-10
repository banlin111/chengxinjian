package com.chengxinjian.catalog;

import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.auth.TokenSecrets;
import com.chengxinjian.common.AuditService;
import com.chengxinjian.common.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.chengxinjian.catalog.CategoryModels.*;

@Service
public class CategoryService {
    private static final String CREATE_OPERATION = "POST /admin/content/categories";
    private static final RowMapper<Category> MAPPER = (r,n) -> new Category(r.getString("id"), r.getString("name"),
        r.getString("kind"), r.getInt("sort_order"), r.getString("status"), r.getInt("version"),
        r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    public CategoryService(JdbcTemplate jdbc, Clock clock, AuditService audit, ObjectMapper json) {
        this.jdbc = jdbc; this.clock = clock; this.audit = audit; this.json = json;
    }
    public List<Category> published(String kind) {
        return jdbc.query("SELECT * FROM content_category WHERE kind=? AND status='published' ORDER BY sort_order,created_at,id", MAPPER, kind);
    }
    public Page<Category> list(AdminPrincipal admin, int page, int pageSize, String q, String status) {
        requireGlobalScope(admin);
        var conditions = new StringBuilder(" WHERE 1=1");
        var args = new ArrayList<Object>();
        if (q != null && !q.isBlank()) {
            conditions.append(" AND name LIKE ? ESCAPE '!'");
            args.add("%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        if (status != null) { conditions.append(" AND status=?"); args.add(status); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM content_category" + conditions, Long.class, args.toArray());
        args.add(pageSize); args.add(((long)page - 1) * pageSize);
        var items = jdbc.query("SELECT * FROM content_category" + conditions + " ORDER BY sort_order,created_at,id LIMIT ? OFFSET ?", MAPPER, args.toArray());
        return new Page<>(items, page, pageSize, total == null ? 0 : total);
    }
    public Category get(AdminPrincipal admin, String id) { requireGlobalScope(admin); return find(id); }
    private Category find(String id) {
        return jdbc.query("SELECT * FROM content_category WHERE id=?", MAPPER, id).stream().findFirst()
            .orElseThrow(BusinessException::notFound);
    }
    @Transactional
    public Category create(AdminPrincipal admin, String key, Create input) {
        requireGlobalScope(admin);
        if (key.length() != 36) { throw BusinessException.invalid("Idempotency-Key 必须为 UUID"); }
        try { UUID.fromString(key); } catch (IllegalArgumentException e) { throw BusinessException.invalid("Idempotency-Key 必须为 UUID"); }
        String payload = serialize(input);
        String hash = TokenSecrets.hash(payload);
        // 锁定同一操作者，避免两个相同创建请求同时越过幂等校验。
        jdbc.queryForObject("SELECT id FROM admin_account WHERE id=? FOR UPDATE", String.class, admin.id());
        var records = jdbc.queryForList("SELECT request_hash,result_json FROM idempotency_record WHERE actor_id=? AND operation=? AND idempotency_key=?",
            admin.id(), CREATE_OPERATION, key);
        if (!records.isEmpty()) {
            if (!hash.equals(records.getFirst().get("request_hash"))) {
                throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "相同幂等标识不能用于不同请求");
            }
            try { return json.readValue((String) records.getFirst().get("result_json"), Category.class); }
            catch (JsonProcessingException e) { throw new IllegalStateException("Unreadable persisted response", e); }
        }
        String id = UUID.randomUUID().toString();
        var now = Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO content_category(id,name,kind,sort_order,status,version,created_at,updated_at) VALUES (?,?,?,?,?,1,?,?)",
            id, input.name(), input.kind(), input.sort(), input.status(), now, now);
        Category result = find(id);
        jdbc.update("""
            INSERT INTO idempotency_record(actor_id,operation,idempotency_key,request_hash,result_id,result_json,created_at)
            VALUES (?,?,?,?,?,?,?)
            """, admin.id(), CREATE_OPERATION, key, hash, id, serialize(result), now);
        audit.record(admin.id(), "content.category.create", id, null);
        return result;
    }
    @Transactional
    public Category update(AdminPrincipal admin, String id, Update input) {
        requireGlobalScope(admin);
        find(id);
        int changed = jdbc.update("""
            UPDATE content_category SET name=?,kind=?,sort_order=?,status=?,version=version+1,updated_at=? WHERE id=? AND version=?
            """, input.name(), input.kind(), input.sort(), input.status(), Timestamp.from(clock.instant()), id, input.version());
        if (changed == 0) { throw versionConflict(); }
        audit.record(admin.id(), "content.category.update", id, null);
        return find(id);
    }
    @Transactional
    public void archive(AdminPrincipal admin, String id, Archive input) {
        requireGlobalScope(admin);
        var rows = jdbc.query("SELECT * FROM content_category WHERE id=? FOR UPDATE", MAPPER, id);
        if (rows.isEmpty()) { throw BusinessException.notFound(); }
        Category current = rows.getFirst();
        if (current.status().equals("archived")) { return; }
        if (current.version() != input.version()) { throw versionConflict(); }
        jdbc.update("UPDATE content_category SET status='archived',version=version+1,updated_at=? WHERE id=?",
            Timestamp.from(clock.instant()), id);
        audit.record(admin.id(), "content.category.archive", id, input.reason());
    }
    private void requireGlobalScope(AdminPrincipal admin) {
        if (admin == null || !"all".equals(admin.dataScope())) { throw BusinessException.forbidden(); }
    }
    private BusinessException versionConflict() {
        return new BusinessException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "内容已被修改，请刷新后重试");
    }
    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize response", e); }
    }
}
