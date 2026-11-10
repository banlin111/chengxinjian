package com.chengxinjian.auth;

import com.chengxinjian.common.ApiResponse;
import com.chengxinjian.user.UserSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http, AdminAuthService auth, ObjectMapper json,
                                      AuthProperties properties, UserSessionService users) throws Exception {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(properties.allowedOrigins().split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
        cors.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cors.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Idempotency-Key"));
        cors.setExposedHeaders(Arrays.asList("X-Request-ID", "Retry-After"));
        cors.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/api/**", cors);
        return http.csrf(c -> c.disable()).cors(c -> c.configurationSource(source))
            .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable()).formLogin(c -> c.disable()).httpBasic(c -> c.disable())
            .authorizeHttpRequests(c -> c
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness", "/api/v1/categories", "/api/v1/agreements/*", "/api/v1/teachers", "/api/v1/teachers/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/login", "/api/v1/admin/auth/refresh", "/api/v1/auth/wechat/login", "/api/v1/auth/refresh").permitAll()
                .requestMatchers("/api/v1/admin/**").authenticated()
                .requestMatchers(HttpMethod.POST,"/api/v1/uploads","/api/v1/uploads/*/complete").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/v1/assets/*").authenticated()
                .requestMatchers("/api/v1/me", "/api/v1/me/**", "/api/v1/auth/logout").hasRole("USER")
                .anyRequest().denyAll())
            .exceptionHandling(c -> c
                .authenticationEntryPoint((req,res,e) -> {
                    res.setStatus(401); res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    json.writeValue(res.getOutputStream(), ApiResponse.error("UNAUTHENTICATED", "请先登录", null));
                })
                .accessDeniedHandler((req,res,e) -> {
                    res.setStatus(403); res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    json.writeValue(res.getOutputStream(), ApiResponse.error("FORBIDDEN", "无权进行此操作", null));
                }))
            .addFilterBefore(new BearerTokenFilter(auth, users, json), UsernamePasswordAuthenticationFilter.class).build();
    }
}
