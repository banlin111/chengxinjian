package com.chengxinjian.auth;

import com.chengxinjian.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAuthController {
    public record LoginInput(@NotBlank @Size(max=64) String username,
        @NotBlank @Size(min=12, max=64) String password,
        @NotBlank @Size(max=2048) String captchaTicket, @Size(max=256) String captchaRandstr, @Size(max=32) String otp) {
        @Override public String toString() { return "LoginInput[redacted]"; }
    }
    public record RefreshInput(@NotBlank @Size(max=256) String refreshToken) {}
    public record OK(boolean success) {}
    private final AdminAuthService auth;
    public AdminAuthController(AdminAuthService auth) { this.auth = auth; }
    @PostMapping("/auth/login")
    ApiResponse<AdminAuthService.LoginResult> login(@Valid @RequestBody LoginInput input, HttpServletRequest request) {
        // 不信任客户端传入的 X-Forwarded-For；反向代理上线时需显式设置可信代理。
        return ApiResponse.ok(auth.login(input, request.getRemoteAddr()));
    }
    @PostMapping("/auth/refresh")
    ApiResponse<AdminAuthService.Tokens> refresh(@Valid @RequestBody RefreshInput input) {
        return ApiResponse.ok(auth.refresh(input.refreshToken()));
    }
    @PostMapping("/auth/logout")
    ApiResponse<OK> logout(@AuthenticationPrincipal AdminPrincipal principal, @Valid @RequestBody RefreshInput input) {
        auth.logout(principal, input.refreshToken()); return ApiResponse.ok(new OK(true));
    }
    @GetMapping("/me")
    ApiResponse<AdminPrincipal> me(@AuthenticationPrincipal AdminPrincipal admin) { return ApiResponse.ok(admin); }
}
