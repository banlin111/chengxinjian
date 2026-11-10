package com.chengxinjian.user;

import com.chengxinjian.common.BusinessException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import static com.chengxinjian.user.UserModels.*;

@Service
public class AgreementService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Environment environment;
    public AgreementService(JdbcTemplate jdbc, Clock clock, Environment environment) {
        this.jdbc=jdbc; this.clock=clock; this.environment=environment;
    }
    public Agreement get(AgreementType type, String version) {
        if (version!=null && (version.isBlank() || version.length()>64)) { throw BusinessException.invalid("协议版本不正确"); }
        var rows=jdbc.query("""
            SELECT * FROM agreement_version WHERE agreement_type=? AND published=TRUE AND effective_at<=?
            AND (test_only=FALSE OR ?=TRUE)
            """+(version==null ? " ORDER BY effective_at DESC, version DESC" : " AND version=?"),
            (rs,n)->new Agreement(AgreementType.valueOf(rs.getString("agreement_type")),rs.getString("version"),
                rs.getString("title"),rs.getString("content"),rs.getTimestamp("effective_at").toInstant()),
            version==null ? new Object[]{type.name(),Timestamp.from(clock.instant()),local()} :
                new Object[]{type.name(),Timestamp.from(clock.instant()),local(),version});
        if (rows.isEmpty()) {
            if (version!=null) { throw BusinessException.notFound(); }
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"AGREEMENTS_UNAVAILABLE","当前协议尚未配置，请稍后再试");
        }
        return rows.getFirst();
    }
    public List<Agreement> required() { return List.of(get(AgreementType.privacy,null),get(AgreementType.user,null)); }
    public void validateReferences(List<AgreementRef> refs, List<Agreement> current) {
        var seen=EnumSet.noneOf(AgreementType.class);
        for (var ref:refs) {
            if (!seen.add(ref.type())) { throw BusinessException.invalid("同一种协议不能重复提交"); }
            var match=current.stream().filter(a->a.type()==ref.type()).findFirst();
            if (match.isEmpty() || !match.get().version().equals(ref.version())) {
                throw new BusinessException(HttpStatus.CONFLICT,"AGREEMENT_VERSION_CHANGED","请阅读并同意当前版本协议");
            }
        }
    }
    public void consent(String userId, List<AgreementRef> refs, List<Agreement> current) {
        // Called with the user row locked; repeat logins preserve the first consent evidence.
        for (var agreement:current) {
            long count=jdbc.queryForObject("SELECT COUNT(*) FROM user_agreement_consent WHERE user_id=? AND agreement_type=? AND agreement_version=?",
                Long.class,userId,agreement.type().name(),agreement.version());
            if (count>0) { continue; }
            if (refs.stream().noneMatch(r->r.type()==agreement.type() && r.version().equals(agreement.version()))) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,"AGREEMENT_REQUIRED","请先阅读并同意用户协议和隐私政策");
            }
            jdbc.update("INSERT INTO user_agreement_consent(user_id,agreement_type,agreement_version,consented_at,request_id) VALUES (?,?,?,?,?)",
                userId,agreement.type().name(),agreement.version(),Timestamp.from(clock.instant()),Objects.requireNonNullElse(MDC.get("requestId"),"system"));
        }
    }
    private boolean local() { return environment.acceptsProfiles(Profiles.of("local")); }
}
