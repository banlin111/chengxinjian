package com.chengxinjian.auth;

import com.chengxinjian.common.BusinessException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRepository {
    public record Account(String id, String username, String displayName, String passwordHash,
                          boolean enabled, String dataScope, String teacherId) {}
    private final JdbcTemplate jdbc;
    public AdminRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private Optional<Account> find(String column, String value) {
        return jdbc.query("SELECT * FROM admin_account WHERE " + column + " = ?",
            (rs, n) -> new Account(rs.getString("id"), rs.getString("username"), rs.getString("display_name"),
                rs.getString("password_hash"), rs.getBoolean("enabled"), rs.getString("data_scope"), rs.getString("teacher_id")),
            value).stream().findFirst();
    }
    public Optional<Account> byUsername(String username) { return find("username", username); }
    public Optional<Account> byId(String id) { return find("id", id); }
    public AdminPrincipal principal(String id) {
        Account a = byId(id).filter(Account::enabled).orElseThrow(BusinessException::unauthenticated);
        List<String> roles = jdbc.queryForList("SELECT role_id FROM admin_account_role WHERE account_id = ? ORDER BY role_id", String.class, id);
        List<String> permissions = jdbc.queryForList("""
            SELECT DISTINCT rp.permission_code FROM admin_role_permission rp
            JOIN admin_account_role ar ON ar.role_id = rp.role_id
            WHERE ar.account_id = ? ORDER BY rp.permission_code
            """, String.class, id);
        return new AdminPrincipal(a.id(), a.username(), a.displayName(), roles, permissions, a.dataScope(), a.teacherId());
    }
}
