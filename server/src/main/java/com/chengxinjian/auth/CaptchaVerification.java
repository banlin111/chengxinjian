package com.chengxinjian.auth;

import com.chengxinjian.common.BusinessException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
public class CaptchaVerification {
    private final AuthProperties auth; private final CaptchaProperties properties;
    private final Environment environment; private final CaptchaGateway gateway; private final CaptchaConsumption consumption;
    public CaptchaVerification(AuthProperties auth,CaptchaProperties properties,Environment environment,CaptchaGateway gateway,CaptchaConsumption consumption) {
        this.auth=auth;this.properties=properties;this.environment=environment;this.gateway=gateway;this.consumption=consumption;
    }
    public void verify(String ticket,String randstr,String address) {
        if ("local".equals(auth.captchaMode())) {
            if (!environment.acceptsProfiles(Profiles.of("local")) || environment.acceptsProfiles(Profiles.of("prod")) || auth.localCaptchaTicket().isBlank()
                    || !TokenSecrets.equal(ticket,auth.localCaptchaTicket())) throw BusinessException.invalid("验证码校验失败");
            return;
        }
        if (!"tencent".equals(auth.captchaMode()) || !properties.configured()) throw TencentCaptchaGateway.unavailable();
        if (ticket==null || ticket.isBlank() || ticket.length()>2048 || ticket.startsWith("trerror")
                || randstr==null || randstr.isBlank() || randstr.length()>256) throw BusinessException.invalid("请完成验证码并提交 ticket 和 randstr");
        // Commit before provider I/O; a failed/ambiguous verification requires a fresh challenge.
        consumption.claim(ticket);
        gateway.verify(ticket,randstr,address);
    }
}
