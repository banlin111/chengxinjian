package com.chengxinjian.user;

import com.chengxinjian.auth.AdminAuthService.Tokens;
import com.chengxinjian.auth.AuthProperties;
import com.chengxinjian.auth.TokenSecrets;
import com.chengxinjian.common.AuditService;
import com.chengxinjian.common.BusinessException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSessionService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AuthProperties config;
    private final UserService users;
    private final AuditService audit;
    public UserSessionService(JdbcTemplate jdbc, Clock clock, AuthProperties config, UserService users, AuditService audit) {
        this.jdbc=jdbc; this.clock=clock; this.config=config; this.users=users; this.audit=audit;
    }
    private Tokens newTokens() { return new Tokens(TokenSecrets.generate(),TokenSecrets.generate(),config.accessTtl().toSeconds(),config.refreshTtl().toSeconds(),"Bearer"); }
    @Transactional
    public Tokens create(String id) {
        var t=newTokens(); var now=clock.instant();
        jdbc.update("""
            INSERT INTO user_auth_session(id,user_id,audience,access_hash,refresh_hash,access_expires_at,refresh_expires_at,revoked,created_at)
            VALUES (?,?,'user',?,?,?,?,FALSE,?)
            """,UUID.randomUUID().toString(),id,TokenSecrets.hash(t.accessToken()),TokenSecrets.hash(t.refreshToken()),
            Timestamp.from(now.plus(config.accessTtl())),Timestamp.from(now.plus(config.refreshTtl())),Timestamp.from(now));
        return t;
    }
    public UserPrincipal authenticate(String token) {
        var rows=jdbc.queryForList("SELECT user_id,access_expires_at FROM user_auth_session WHERE access_hash=? AND audience='user' AND revoked=FALSE",TokenSecrets.hash(token));
        if (rows.isEmpty()) { throw BusinessException.unauthenticated(); }
        var row=rows.getFirst();
        if (!((Timestamp)row.get("access_expires_at")).toInstant().isAfter(clock.instant())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,"TOKEN_EXPIRED","登录已过期，请刷新会话");
        }
        String id=(String)row.get("user_id"); users.requireActive(id,false); return new UserPrincipal(id);
    }
    @Transactional
    public Tokens refresh(String token) {
        var rows=jdbc.queryForList("SELECT * FROM user_auth_session WHERE refresh_hash=? AND audience='user' FOR UPDATE",TokenSecrets.hash(token));
        if (rows.isEmpty()) { throw BusinessException.unauthenticated(); }
        var row=rows.getFirst();
        if ((Boolean)row.get("revoked") || !((Timestamp)row.get("refresh_expires_at")).toInstant().isAfter(clock.instant())) { throw BusinessException.unauthenticated(); }
        users.requireActive((String)row.get("user_id"),false);
        var t=newTokens(); var now=clock.instant();
        int changed=jdbc.update("UPDATE user_auth_session SET access_hash=?,refresh_hash=?,access_expires_at=?,refresh_expires_at=? WHERE id=? AND refresh_hash=? AND revoked=FALSE",
            TokenSecrets.hash(t.accessToken()),TokenSecrets.hash(t.refreshToken()),Timestamp.from(now.plus(config.accessTtl())),Timestamp.from(now.plus(config.refreshTtl())),row.get("id"),TokenSecrets.hash(token));
        if (changed!=1) { throw BusinessException.unauthenticated(); }
        return t;
    }
    @Transactional
    public void logout(UserPrincipal principal,String token) {
        int changed=jdbc.update("UPDATE user_auth_session SET revoked=TRUE WHERE user_id=? AND refresh_hash=? AND audience='user' AND revoked=FALSE",principal.id(),TokenSecrets.hash(token));
        if (changed>0) { audit.record(principal.id(),"user.logout",principal.id(),null); }
    }
}
