package com.chengxinjian.user;

import com.chengxinjian.auth.AdminAuthController;
import com.chengxinjian.auth.AdminAuthService;
import com.chengxinjian.auth.LoginThrottle;
import com.chengxinjian.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.chengxinjian.user.UserModels.*;

@RestController
@RequestMapping("/api/v1")
public class UserAuthController {
    private final WechatGateway wechat;
    private final WechatRegistration registration;
    private final UserSessionService sessions;
    private final UserService users;
    private final AgreementService agreements;
    private final LoginThrottle throttle;
    public UserAuthController(WechatGateway wechat,WechatRegistration registration,UserSessionService sessions,
        UserService users,AgreementService agreements,LoginThrottle throttle) {
        this.wechat=wechat; this.registration=registration; this.sessions=sessions; this.users=users; this.agreements=agreements; this.throttle=throttle;
    }
    @GetMapping("/agreements/{type}")
    ApiResponse<Agreement> agreement(@PathVariable AgreementType type,@RequestParam(required=false) String version) { return ApiResponse.ok(agreements.get(type,version)); }
    @PostMapping("/auth/wechat/login")
    ApiResponse<LoginResult> login(@Valid @RequestBody WxLogin input,HttpServletRequest request) {
        throttle.attempt("wechat-login",request.getRemoteAddr());
        var current=agreements.required(); agreements.validateReferences(input.agreements(),current);
        // No DB transaction/row lock is held during the external network call; never retry a code automatically.
        return ApiResponse.ok(registration.complete(wechat.exchange(input.code()),input,current));
    }
    @PostMapping("/auth/refresh")
    ApiResponse<AdminAuthService.Tokens> refresh(@Valid @RequestBody AdminAuthController.RefreshInput input) { return ApiResponse.ok(sessions.refresh(input.refreshToken())); }
    @PostMapping("/auth/logout")
    ApiResponse<AdminAuthController.OK> logout(@AuthenticationPrincipal UserPrincipal principal,@Valid @RequestBody AdminAuthController.RefreshInput input) {
        sessions.logout(principal,input.refreshToken()); return ApiResponse.ok(new AdminAuthController.OK(true));
    }
    @GetMapping("/me")
    ApiResponse<User> me(@AuthenticationPrincipal UserPrincipal principal) { return ApiResponse.ok(users.profile(principal.id())); }
    @GetMapping("/me/settings")
    ApiResponse<Settings> settings(@AuthenticationPrincipal UserPrincipal principal) { return ApiResponse.ok(users.settings(principal.id())); }
    @PutMapping("/me/settings")
    ApiResponse<Settings> settings(@AuthenticationPrincipal UserPrincipal principal,@Valid @RequestBody Settings input) { return ApiResponse.ok(users.updateSettings(principal.id(),input)); }
}
