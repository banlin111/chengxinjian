package com.chengxinjian.auth;

import com.chengxinjian.common.ApiResponse;
import com.chengxinjian.user.UserSessionService;
import com.chengxinjian.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerTokenFilter extends OncePerRequestFilter {
    private final AdminAuthService auth;
    private final ObjectMapper json;
    private final UserSessionService users;
    public BearerTokenFilter(AdminAuthService auth, UserSessionService users, ObjectMapper json) { this.auth = auth; this.users = users; this.json = json; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        // 认证入口独立校验凭据；过期 accessToken 不应阻止合法刷新或重新登录。
        return "POST".equals(request.getMethod()) && ("/api/v1/admin/auth/login".equals(request.getRequestURI())
            || "/api/v1/admin/auth/refresh".equals(request.getRequestURI())
            || "/api/v1/auth/wechat/login".equals(request.getRequestURI())
            || "/api/v1/auth/refresh".equals(request.getRequestURI()));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null) { chain.doFilter(request, response); return; }
        try {
            if (!header.startsWith("Bearer ") || header.length() > 263 || header.length() <= 7) {
                throw BusinessException.unauthenticated();
            }
            if (request.getRequestURI().startsWith("/api/v1/admin/")) {
                AdminPrincipal principal = auth.authenticate(header.substring(7));
                var authorities = principal.permissions().stream().map(SimpleGrantedAuthority::new).toList();
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities));
            } else {
                com.chengxinjian.user.UserPrincipal principal;
                try { principal = users.authenticate(header.substring(7)); }
                catch (BusinessException e) {
                    if (!"UNAUTHENTICATED".equals(e.code())) { throw e; }
                    // Preserve the established 403 for a valid admin credential on user routes.
                    // Expired/revoked admin credentials still return their authentication error.
                    AdminPrincipal admin=auth.authenticate(header.substring(7));
                    String path=request.getRequestURI();
                    if (!(path.equals("/api/v1/uploads") || path.matches("/api/v1/uploads/[^/]+/complete") || path.matches("/api/v1/assets/[^/]+"))) throw BusinessException.forbidden();
                    var authorities=admin.permissions().stream().map(SimpleGrantedAuthority::new).toList();
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin,null,authorities));
                    chain.doFilter(request,response);return;
                }
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            }
        } catch (BusinessException e) {
            response.setStatus(e.status().value()); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getOutputStream(), ApiResponse.error(e.code(), e.getMessage(), null));
            return;
        } catch (DataAccessException e) {
            // 认证发生在 MVC 之前，此处也需遵守统一错误格式，不暴露底层连接异常。
            response.setStatus(503); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getOutputStream(), ApiResponse.error("PROVIDER_UNAVAILABLE", "认证服务暂不可用，请稍后重试", null));
            return;
        }
        chain.doFilter(request, response);
    }
}
