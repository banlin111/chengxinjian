package com.chengxinjian.auth;

import com.chengxinjian.common.AuditService;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LocalAdminBootstrap implements ApplicationRunner {
    private final AuthProperties properties;
    private final Environment environment;
    private final AdminRepository admins;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final AuditService audit;
    public LocalAdminBootstrap(AuthProperties properties, Environment environment, AdminRepository admins,
        JdbcTemplate jdbc, PasswordEncoder passwords, Clock clock, AuditService audit) {
        this.properties = properties; this.environment = environment; this.admins = admins;
        this.jdbc = jdbc; this.passwords = passwords; this.clock = clock; this.audit = audit;
    }
    @Override @Transactional
    public void run(ApplicationArguments arguments) {
        if (properties.accessTtl().isNegative() || properties.accessTtl().isZero()
            || properties.refreshTtl().compareTo(properties.accessTtl()) <= 0) {
            throw new IllegalStateException("Invalid token TTL configuration");
        }
        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        if (production && (!environment.getProperty("spring.datasource.url", "").startsWith("jdbc:mysql:")
            || environment.getProperty("spring.datasource.password", "").isBlank())) {
            throw new IllegalStateException("Production requires a MySQL datasource and a nonempty database password");
        }
        if ((local && production) || (!local && ("local".equals(properties.captchaMode())
            || !properties.bootstrapPassword().isBlank() || !properties.localCaptchaTicket().isBlank()))) {
            throw new IllegalStateException("Local authentication settings cannot be enabled outside the local profile");
        }
        if (!local || properties.bootstrapPassword().isBlank()) { return; }
        String password = properties.bootstrapPassword();
        // BCrypt 输入上限为 72 字节，汉字密码按 UTF-8 字节检查。
        if (password.length() < 12 || password.length() > 64 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
            || properties.bootstrapUsername().isBlank() || properties.localCaptchaTicket().length() < 16) {
            throw new IllegalStateException("Set a 12-64 character admin password (up to 72 UTF-8 bytes) and a local captcha ticket of at least 16 characters");
        }
        if (admins.byUsername(properties.bootstrapUsername()).isPresent()) { return; }
        String id = UUID.randomUUID().toString();
        var now = Timestamp.from(clock.instant());
        jdbc.update("""
            INSERT INTO admin_account(id,username,display_name,password_hash,enabled,data_scope,teacher_id,version,created_at,updated_at)
            VALUES (?,?,?,?,TRUE,'all',NULL,1,?,?)
            """, id, properties.bootstrapUsername(), "本地开发管理员", passwords.encode(password), now, now);
        jdbc.update("INSERT INTO admin_account_role(account_id,role_id) VALUES (?,'role-content-manager')", id);
        audit.record(id, "admin.bootstrap.local", id, "本地开发专用初始化");
    }
}
