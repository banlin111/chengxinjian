package com.chengxinjian.user;

import com.chengxinjian.auth.TokenSecrets;
import com.chengxinjian.common.AuditService;
import com.chengxinjian.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.chengxinjian.user.UserModels.*;

@Service
public class WechatRegistration {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AgreementService agreements;
    private final UserService users;
    private final UserSessionService sessions;
    private final AuditService audit;
    public WechatRegistration(JdbcTemplate jdbc,Clock clock,AgreementService agreements,UserService users,UserSessionService sessions,AuditService audit) {
        this.jdbc=jdbc; this.clock=clock; this.agreements=agreements; this.users=users; this.sessions=sessions; this.audit=audit;
    }
    // MySQL REPEATABLE READ can retain a pre-lock snapshot and miss the winning login's consents.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public LoginResult complete(WechatGateway.Identity identity, WxLogin input, List<Agreement> expected) {
        var current=agreements.required();
        if (!current.equals(expected)) { throw new BusinessException(HttpStatus.CONFLICT,"AGREEMENT_VERSION_CHANGED","协议已更新，请重新阅读并同意"); }
        String hash=TokenSecrets.hash(identity.appId()+":"+identity.openId());
        var now=Timestamp.from(clock.instant());
        try { jdbc.update("INSERT INTO wechat_code_use(code_hash,used_at) VALUES (?,?)",TokenSecrets.hash(identity.appId()+":"+input.code()),now); }
        catch (DuplicateKeyException e) { throw new BusinessException(HttpStatus.BAD_REQUEST,"WX_CODE_INVALID","登录凭证已使用，请重新取得"); }
        // A duplicate INSERT followed by FOR UPDATE can deadlock when existing identities
        // receive concurrent logins (both transactions first take a shared duplicate-key lock).
        // The no-op upsert acquires an exclusive lock directly on both MySQL and H2 MySQL mode.
        jdbc.update("""
            INSERT INTO wechat_identity(identity_hash,app_id,open_id,user_id,created_at) VALUES (?,?,?,NULL,?)
            ON DUPLICATE KEY UPDATE identity_hash=identity_hash
            """,hash,bytes(identity.appId()),bytes(identity.openId()),now);
        var row=jdbc.queryForMap("SELECT user_id FROM wechat_identity WHERE identity_hash=? FOR UPDATE",hash);
        String id=(String)row.get("user_id"); boolean isNew=id==null;
        if (isNew) {
            id=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO app_user(id,nickname,status,version,created_at,updated_at,last_login_at) VALUES (?,?,'active',1,?,?,?)",id,"澄心用户",now,now,now);
            jdbc.update("INSERT INTO user_settings(user_id,settings_json,version) VALUES (?,?,1)",id,users.encode(Settings.defaults()));
            jdbc.update("UPDATE wechat_identity SET user_id=? WHERE identity_hash=?",id,hash);
        }
        users.requireActive(id,true);
        agreements.consent(id,input.agreements(),current);
        if (identity.unionId()!=null) { jdbc.update("UPDATE wechat_identity SET union_id=? WHERE identity_hash=?",bytes(identity.unionId()),hash); }
        jdbc.update("UPDATE app_user SET last_login_at=? WHERE id=?",now,id);
        var tokens=sessions.create(id);
        audit.record(id,isNew ? "user.register" : "user.login",id,null);
        return new LoginResult(tokens.accessToken(),tokens.refreshToken(),tokens.expiresIn(),tokens.refreshExpiresIn(),tokens.tokenType(),users.profile(id),isNew,true);
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
