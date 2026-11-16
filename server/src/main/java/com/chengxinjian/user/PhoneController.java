package com.chengxinjian.user;

import com.chengxinjian.auth.LoginThrottle;
import com.chengxinjian.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.SecureRandom;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PhoneController {
    record SmsSend(@NotNull @Pattern(regexp="1[3-9][0-9]{9}") String phone,@NotBlank String purpose,
                   @NotBlank @Size(max=2048) String captchaTicket) {}
    record Receipt(String challengeId,int retryAfterSeconds,int expiresIn) {}
    record SmsReauth(@NotBlank @Size(max=36) String challengeId,@NotNull @Pattern(regexp="[0-9]{6}") String code,
                     @NotBlank String purpose) {}
    record SmsBind(@NotBlank @Size(max=36) String challengeId,@NotNull @Pattern(regexp="[0-9]{6}") String code,
                   @NotBlank @Size(max=256) String reauthToken) {}
    record WxReauth(@NotBlank @Size(max=256) String code,@NotBlank String purpose) {}
    record WxBind(@NotBlank @Size(max=256) String phoneCode,@Size(max=256) String reauthToken) {}
    private final SecureRandom random=new SecureRandom();
    private final PhoneSecurity security;
    private final WechatGateway wechat;
    private final WechatPhoneGateway phones;
    private final PhoneProviders sms;
    private final PasswordEncoder passwords;
    private final LoginThrottle throttle;
    PhoneController(PhoneSecurity security,WechatGateway wechat,WechatPhoneGateway phones,PhoneProviders sms,
                    PasswordEncoder passwords,LoginThrottle throttle) {
        this.security=security; this.wechat=wechat; this.phones=phones; this.sms=sms; this.passwords=passwords; this.throttle=throttle;
    }
    private void changePurpose(String purpose) {
        if (!"change_phone".equals(purpose)) throw new BusinessException(HttpStatus.BAD_REQUEST,"UNSUPPORTED_PURPOSE","此操作仅支持手机号验证");
    }
    @PostMapping("/auth/sms")
    ApiResponse<Receipt> send(@AuthenticationPrincipal UserPrincipal actor,@RequestBody @Valid SmsSend body,HttpServletRequest request) {
        if (!"reauth".equals(body.purpose())) changePurpose(body.purpose());
        String ip=request.getRemoteAddr();
        throttle.attempt("phone-sms:"+actor.id(),ip);
        if ("reauth".equals(body.purpose())) security.checkCurrentPhone(actor,body.phone());
        sms.checkCaptcha(body.captchaTicket(),ip);
        throttle.smsSend(body.phone(),actor.id(),ip);
        String code=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
        String challenge=security.reserve(actor,body.phone(),body.purpose(),passwords.encode(code));
        sms.send(body.phone(),code);
        security.markSent(challenge);
        return ApiResponse.ok(new Receipt(challenge,60,300));
    }
    @PostMapping("/auth/reauth/wechat")
    ApiResponse<PhoneSecurity.Reauth> reauth(@AuthenticationPrincipal UserPrincipal actor,@RequestBody @Valid WxReauth body,HttpServletRequest request) {
        changePurpose(body.purpose()); throttle.attempt("phone-wx:"+actor.id(),request.getRemoteAddr());
        return ApiResponse.ok(security.reauthWechat(actor,wechat.exchange(body.code()),body.code()));
    }
    @PostMapping("/auth/reauth")
    ApiResponse<PhoneSecurity.Reauth> reauth(@AuthenticationPrincipal UserPrincipal actor,@RequestBody @Valid SmsReauth body) {
        changePurpose(body.purpose());
        return ApiResponse.ok(security.reauthSms(actor,body.challengeId(),body.code()));
    }
    @PutMapping("/me/phone")
    ApiResponse<UserModels.User> bind(@AuthenticationPrincipal UserPrincipal actor,@RequestBody @Valid SmsBind body) {
        return ApiResponse.ok(security.bindSms(actor,body.challengeId(),body.code(),body.reauthToken()));
    }
    @PutMapping("/me/phone/wechat")
    ApiResponse<UserModels.User> bind(@AuthenticationPrincipal UserPrincipal actor,@RequestBody @Valid WxBind body,HttpServletRequest request) {
        throttle.attempt("phone-code:"+actor.id(),request.getRemoteAddr());
        security.checkBeforeWechatPhone(actor,body.reauthToken());
        return ApiResponse.ok(security.bindWechat(actor,phones.exchange(body.phoneCode()),body.phoneCode(),body.reauthToken()));
    }
}
