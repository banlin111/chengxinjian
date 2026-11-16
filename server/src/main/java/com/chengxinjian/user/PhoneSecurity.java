package com.chengxinjian.user;

import com.chengxinjian.auth.TokenSecrets;
import com.chengxinjian.common.AuditService;
import com.chengxinjian.common.BusinessException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = BusinessException.class)
public class PhoneSecurity {
    record Reauth(String reauthToken, int expiresIn) {
        @Override public String toString() { return "Reauth[redacted]"; }
    }
    private record Binding(String phone, int version) {}
    private final JdbcTemplate jdbc;
    private final UserService users;
    private final AuditService audit;
    private final Clock clock;
    private final PasswordEncoder passwords;

    PhoneSecurity(JdbcTemplate jdbc, UserService users, AuditService audit, Clock clock, PasswordEncoder passwords) {
        this.jdbc=jdbc; this.users=users; this.audit=audit; this.clock=clock; this.passwords=passwords;
    }
    private Timestamp now() { return Timestamp.from(clock.instant()); }
    private BusinessException invalidProof() {
        return new BusinessException(HttpStatus.BAD_REQUEST,"PHONE_PROOF_INVALID","验证凭证无效或已过期");
    }
    private BusinessException reauthRequired() {
        return new BusinessException(HttpStatus.FORBIDDEN,"REAUTH_REQUIRED","请重新验证本账号身份");
    }
    private Binding lock(UserPrincipal actor) {
        users.requireActive(actor.id(),true);
        if (jdbc.queryForList("SELECT id FROM user_auth_session WHERE id=? AND user_id=? AND revoked=FALSE AND access_expires_at>? FOR UPDATE",
                String.class,actor.sessionId(),actor.id(),now()).isEmpty()) throw BusinessException.unauthenticated();
        return jdbc.queryForObject("SELECT phone,phone_version FROM app_user WHERE id=?",
            (rs,n)->new Binding(rs.getString(1),rs.getInt(2)), actor.id());
    }
    public void checkBeforeWechatPhone(UserPrincipal actor,String token) {
        var current=lock(actor);
        if (current.phone()!=null || token!=null) verifyReauth(actor,current,token,false);
    }
    private void verifyReauth(UserPrincipal actor,Binding current,String token,boolean consume) {
        if (token==null || token.isBlank()) throw reauthRequired();
        String filter=" WHERE token_hash=? AND session_id=? AND user_id=? AND phone_version=? AND purpose='change_phone' AND expires_at>?";
        Object[] args={TokenSecrets.hash(token),actor.sessionId(),actor.id(),current.version(),now()};
        int matches=consume ? jdbc.update("DELETE FROM phone_reauth"+filter,args)
            : jdbc.queryForObject("SELECT COUNT(*) FROM phone_reauth"+filter,Integer.class,args);
        if (matches!=1) throw reauthRequired();
    }
    private void claimWechatCode(String digest) {
        try { jdbc.update("INSERT INTO wechat_code_use(code_hash,used_at) VALUES (?,?)",digest,now()); }
        catch (DuplicateKeyException ex) { throw invalidProof(); }
    }
    public Reauth reauthWechat(UserPrincipal actor,WechatGateway.Identity identity,String code) {
        var current=lock(actor);
        String digest=TokenSecrets.hash(identity.appId()+":"+identity.openId());
        claimWechatCode(TokenSecrets.hash(identity.appId()+":"+code));
        if (jdbc.queryForList("SELECT user_id FROM wechat_identity WHERE identity_hash=? AND user_id=?",
                String.class,digest,actor.id()).isEmpty()) throw reauthRequired();
        return issue(actor,current);
    }
    private Reauth issue(UserPrincipal actor,Binding current) {
        String token=TokenSecrets.generate();
        jdbc.update("INSERT INTO phone_reauth(token_hash,session_id,user_id,purpose,phone_version,expires_at) VALUES (?,?,?,'change_phone',?,?)",
            TokenSecrets.hash(token),actor.sessionId(),actor.id(),current.version(),Timestamp.from(clock.instant().plusSeconds(300)));
        return new Reauth(token,300);
    }
    public void checkCurrentPhone(UserPrincipal actor,String phone) {
        if (!phone.equals(lock(actor).phone())) throw reauthRequired();
    }
    public String reserve(UserPrincipal actor,String phone,String purpose,String hash) {
        var current=lock(actor);
        if ("reauth".equals(purpose) && !phone.equals(current.phone())) throw reauthRequired();
        jdbc.update("UPDATE phone_sms_challenge SET state='spent' WHERE user_id=? AND session_id=? AND purpose=? AND state IN ('pending','sent')",
            actor.id(),actor.sessionId(),purpose);
        String id=UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO phone_sms_challenge(id,session_id,user_id,phone,phone_version,purpose,code_hash,state,expires_at)
            VALUES (?,?,?,?,?,?,?,'pending',?)
            """,id,actor.sessionId(),actor.id(),phone,current.version(),purpose,hash,Timestamp.from(clock.instant().plusSeconds(300)));
        return id;
    }
    public void markSent(String id) {
        if (jdbc.update("UPDATE phone_sms_challenge SET state='sent' WHERE id=? AND state='pending' AND expires_at>?",id,now())!=1)
            throw invalidProof();
    }
    private String consumeSms(UserPrincipal actor,Binding current,String purpose,String id,String code) {
        var rows=jdbc.queryForList("""
            SELECT phone,code_hash FROM phone_sms_challenge WHERE id=? AND session_id=? AND user_id=? AND phone_version=?
            AND purpose=? AND state='sent' AND attempts<5 AND expires_at>? FOR UPDATE
            """,id,actor.sessionId(),actor.id(),current.version(),purpose,now());
        if (rows.isEmpty()) throw invalidProof();
        jdbc.update("UPDATE phone_sms_challenge SET attempts=attempts+1 WHERE id=?",id);
        var row=rows.getFirst();
        if (!passwords.matches(code,(String)row.get("code_hash"))) throw invalidProof();
        jdbc.update("UPDATE phone_sms_challenge SET state='spent' WHERE id=?",id);
        return (String)row.get("phone");
    }
    public Reauth reauthSms(UserPrincipal actor,String id,String code) {
        var current=lock(actor);
        if (!consumeSms(actor,current,"reauth",id,code).equals(current.phone())) throw reauthRequired();
        return issue(actor,current);
    }
    public UserModels.User bindSms(UserPrincipal actor,String id,String code,String token) {
        var current=lock(actor);
        verifyReauth(actor,current,token,false);
        String phone=consumeSms(actor,current,"change_phone",id,code);
        verifyReauth(actor,current,token,true);
        return assign(actor,current,phone);
    }
    public UserModels.User bindWechat(UserPrincipal actor,String phone,String code,String token) {
        var current=lock(actor);
        claimWechatCode(TokenSecrets.hash("phone:"+code));
        if (current.phone()!=null || token!=null) verifyReauth(actor,current,token,true);
        return assign(actor,current,phone);
    }
    private UserModels.User assign(UserPrincipal actor,Binding current,String phone) {
        if (!phone.matches("1[3-9][0-9]{9}")) throw BusinessException.invalid("仅支持中国大陆手机号");
        if (!phone.equals(current.phone())) {
            jdbc.queryForObject("SELECT id FROM phone_binding_control WHERE id=1 FOR UPDATE",Integer.class);
            if (!jdbc.queryForList("SELECT id FROM app_user WHERE phone=?",String.class,phone).isEmpty()) throw conflict();
            try {
                jdbc.update("UPDATE app_user SET phone=?,phone_version=phone_version+1,version=version+1,updated_at=? WHERE id=?",
                    phone,now(),actor.id());
            } catch (DuplicateKeyException ex) { throw conflict(); }
            audit.record(actor.id(),current.phone()==null ? "user.bind_phone" : "user.change_phone",actor.id(),null);
        }
        return users.profile(actor.id());
    }
    private BusinessException conflict() {
        return new BusinessException(HttpStatus.CONFLICT,"PHONE_ALREADY_BOUND","此手机号已绑定其他账号");
    }
    @Scheduled(fixedDelay=300000,initialDelay=300000)
    public void purgeExpired() {
        jdbc.update("DELETE FROM phone_sms_challenge WHERE expires_at<=?",now());
        jdbc.update("DELETE FROM phone_reauth WHERE expires_at<=?",now());
    }
}
