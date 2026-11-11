package com.chengxinjian.auth;

import com.chengxinjian.common.AuditService;
import com.chengxinjian.common.BusinessException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {
    public record Tokens(String accessToken, String refreshToken, long expiresIn, long refreshExpiresIn, String tokenType) {}
    public record LoginResult(String accessToken, String refreshToken, long expiresIn, long refreshExpiresIn,
                              String tokenType, AdminPrincipal admin) {}
    private final JdbcTemplate jdbc;
    private final AdminRepository admins;
    private final PasswordEncoder passwords;
    private final AuthProperties properties;
    private final CaptchaVerification captcha;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final Clock clock;
    private final AuditService audit;
    private final LoginThrottle throttle;
    private final String dummyPasswordHash;

    public AdminAuthService(JdbcTemplate jdbc, AdminRepository admins, PasswordEncoder passwords,
        AuthProperties properties, CaptchaVerification captcha, Clock clock, AuditService audit, LoginThrottle throttle,
        org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc; this.admins = admins; this.passwords = passwords; this.properties = properties;
        this.captcha = captcha; this.transactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager); this.clock = clock; this.audit = audit; this.throttle = throttle;
        this.dummyPasswordHash = passwords.encode(TokenSecrets.generate());
    }
    public LoginResult login(AdminAuthController.LoginInput input, String remoteAddress) {
        throttle.attempt(input.username(), remoteAddress);
        captcha.verify(input.captchaTicket(),input.captchaRandstr(),remoteAddress);
        return transactions.execute(status -> passwordLogin(input));
    }
    private LoginResult passwordLogin(AdminAuthController.LoginInput input) {
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw BusinessException.invalid("密码超出允许长度");
        }
        if (input.otp() != null && !input.otp().isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE", "暂未配置二次验证服务");
        }
        jdbc.queryForList("SELECT id FROM admin_account WHERE username=? FOR UPDATE",input.username());
        var account = admins.byUsername(input.username());
        boolean valid = passwords.matches(input.password(), account.map(AdminRepository.Account::passwordHash).orElse(dummyPasswordHash));
        if (!valid || account.isEmpty() || !account.get().enabled()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "账号或密码不正确，或账号不可用");
        }
        String id = account.get().id();
        Tokens tokens = createSession(id);
        audit.record(id, "admin.login", id, null);
        return new LoginResult(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(),
            tokens.refreshExpiresIn(), tokens.tokenType(), admins.principal(id));
    }
    private Tokens newTokens() {
        return new Tokens(TokenSecrets.generate(), TokenSecrets.generate(), properties.accessTtl().toSeconds(),
            properties.refreshTtl().toSeconds(), "Bearer");
    }
    private Tokens createSession(String accountId) {
        Tokens tokens = newTokens();
        var now = clock.instant();
        jdbc.update("""
            INSERT INTO auth_session(id,account_id,audience,access_hash,refresh_hash,
                access_expires_at,refresh_expires_at,revoked,created_at) VALUES (?,?,'admin',?,?,?,?,FALSE,?)
            """, UUID.randomUUID().toString(), accountId, TokenSecrets.hash(tokens.accessToken()),
            TokenSecrets.hash(tokens.refreshToken()), Timestamp.from(now.plus(properties.accessTtl())),
            Timestamp.from(now.plus(properties.refreshTtl())), Timestamp.from(now));
        return tokens;
    }
    @Transactional
    public Tokens refresh(String refreshToken) {
        var sessions = jdbc.queryForList("SELECT * FROM auth_session WHERE refresh_hash = ? AND audience = 'admin' FOR UPDATE",
            TokenSecrets.hash(refreshToken));
        if (sessions.isEmpty()) { throw BusinessException.unauthenticated(); }
        var session = sessions.getFirst();
        var now = clock.instant();
        if ((Boolean) session.get("revoked") || !((Timestamp) session.get("refresh_expires_at")).toInstant().isAfter(now)) {
            throw BusinessException.unauthenticated();
        }
        admins.principal((String) session.get("account_id"));
        Tokens tokens = newTokens();
        jdbc.update("""
            UPDATE auth_session SET access_hash=?,refresh_hash=?,access_expires_at=?,refresh_expires_at=? WHERE id=?
            """, TokenSecrets.hash(tokens.accessToken()), TokenSecrets.hash(tokens.refreshToken()),
            Timestamp.from(now.plus(properties.accessTtl())), Timestamp.from(now.plus(properties.refreshTtl())), session.get("id"));
        return tokens;
    }
    @Transactional
    public void logout(AdminPrincipal principal, String refreshToken) {
        var rows = jdbc.queryForList("SELECT id,account_id FROM auth_session WHERE refresh_hash=? AND account_id=? AND audience='admin' AND revoked=FALSE FOR UPDATE",
            TokenSecrets.hash(refreshToken), principal.id());
        if (rows.isEmpty()) { return; }
        jdbc.update("UPDATE auth_session SET revoked=TRUE WHERE id=?", rows.getFirst().get("id"));
        String actor = (String) rows.getFirst().get("account_id");
        audit.record(actor, "admin.logout", actor, null);
    }
    public AdminPrincipal authenticate(String token) {
        var rows = jdbc.queryForList("SELECT account_id,access_expires_at FROM auth_session WHERE access_hash=? AND audience='admin' AND revoked=FALSE",
            TokenSecrets.hash(token));
        if (rows.isEmpty()) { throw BusinessException.unauthenticated(); }
        if (!((Timestamp) rows.getFirst().get("access_expires_at")).toInstant().isAfter(clock.instant())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "登录已过期，请刷新会话");
        }
        return admins.principal((String) rows.getFirst().get("account_id"));
    }
}
