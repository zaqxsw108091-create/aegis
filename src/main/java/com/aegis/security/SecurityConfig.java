package com.aegis.security;

import com.aegis.audit.AuditService;
import com.aegis.auth.JwtAuthenticationFilter;
import com.aegis.auth.JwtService;
import com.aegis.config.AegisSecurityProperties;
import com.aegis.detection.BruteForceProtectionService;
import com.aegis.detection.DetectionMetrics;
import com.aegis.detection.IpBlockFilter;
import com.aegis.detection.IpWhitelist;
import com.aegis.detection.RateLimitFilter;
import com.aegis.detection.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * 보안 설정.
 *
 * 적용:
 *  - 비밀번호 해싱: BCrypt strength 12
 *  - JWT 인증(/api): Authorization: Bearer access-token
 *  - 권한: /api/admin/** 는 ROLE_ADMIN, /api/auth/** 공개, 그 외 인증 필요
 *  - 보안 헤더(CSP/X-Frame-Options/HSTS/nosniff/Referrer-Policy), 권한거부 감사+403
 *  - CSRF 활성화(쿠키 기반). 단, 무상태 JWT API(/api/**)는 CSRF 예외
 *  - 탐지 필터: IpBlockFilter(403) → RateLimitFilter(429)
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt strength 12
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher delegate) {
        return new DefaultAuthenticationEventPublisher(delegate);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           BruteForceProtectionService protection,
                                           RateLimiter rateLimiter,
                                           IpWhitelist ipWhitelist,
                                           DetectionMetrics detectionMetrics,
                                           AegisSecurityProperties props,
                                           AuditService auditService,
                                           JwtService jwtService,
                                           ObjectMapper objectMapper) throws Exception {
        IpBlockFilter ipBlockFilter = new IpBlockFilter(protection, ipWhitelist, objectMapper);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimiter, ipWhitelist, auditService,
                detectionMetrics, objectMapper, props.rateLimit().requestsPerMinute());
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService);
        AuditingAccessDeniedHandler accessDeniedHandler =
                new AuditingAccessDeniedHandler(auditService, objectMapper);

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/actuator/**").authenticated()
                .anyRequest().authenticated()
            )
            // 무상태(JWT) — 서버 세션 미사용
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; "
                        + "frame-ancestors 'none'; "
                        + "object-src 'none'; "
                        + "base-uri 'self'; "
                        + "form-action 'self'"))
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .preload(true)
                        .maxAgeInSeconds(31_536_000))
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
            )
            // CSRF 활성화(쿠키 토큰). 무상태 JWT API는 헤더 토큰을 쓰므로 CSRF 예외.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/**"))
            .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler))
            .addFilterBefore(ipBlockFilter, WebAsyncManagerIntegrationFilter.class)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .httpBasic(basic -> {});
        return http.build();
    }
}
