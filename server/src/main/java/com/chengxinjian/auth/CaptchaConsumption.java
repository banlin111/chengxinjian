package com.chengxinjian.auth;

import com.chengxinjian.common.BusinessException;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaptchaConsumption {
    private final JdbcTemplate jdbc; private final Clock clock;
    public CaptchaConsumption(JdbcTemplate jdbc,Clock clock) { this.jdbc=jdbc; this.clock=clock; }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void claim(String ticket) {
        try { jdbc.update("INSERT INTO captcha_consumption(ticket_hash,consumed_at) VALUES (?,?)",TokenSecrets.hash(ticket),Timestamp.from(clock.instant())); }
        catch (DuplicateKeyException ex) { throw BusinessException.invalid("验证码已使用，请重新验证"); }
    }
}
