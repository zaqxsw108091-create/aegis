package com.aegis.security;

import com.aegis.audit.AuditService;
import com.aegis.detection.BruteForceProtectionService;
import com.aegis.detection.IpBlockFilter;
import com.aegis.detection.RateLimitFilter;
import com.aegis.detection.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * 보안 설정.
 * P2: 인증/JWT 예정.
 *
 * 적용:
 *  - 보안 헤더: CSP, X-Frame-Options(DENY), HSTS, X-Content-Type-Options(nosniff), Referrer-Policy
 *  - CSRF 활성화(쿠키 기반 토큰 저장소)
 *  - 인가 실패(권한거부)는 감사 로그에 기록 후 403 JSON 응답
 *  - 탐지 필터: IpBlockFilter(차단 403) → RateLimitFilter(한도 429)
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 인증 실패/성공 이벤트를 발행하도록 명시적으로 등록한다.
     * 이 빈이 있어야 탐지/감사 리스너가 이벤트를 수신한다.
     */
    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher delegate) {
        return new DefaultAuthenticationEventPublisher(delegate);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           BruteForceProtectionService protection,
                                           RateLimitService rateLimitService,
                                           AuditService auditService,
                                           ObjectMapper objectMapper) throws Exception {
        IpBlockFilter ipBlockFilter = new IpBlockFilter(protection, objectMapper);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimitService, auditService, objectMapper);
        AuditingAccessDeniedHandler accessDeniedHandler =
                new AuditingAccessDeniedHandler(auditService, objectMapper);

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/**").authenticated()
                .anyRequest().authenticated()
            )
            // 보안 헤더
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
                        .maxAgeInSeconds(31_536_000)) // 1년
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                // X-Content-Type-Options: nosniff 는 기본 적용됨
            )
            // CSRF 활성화 (쿠키 기반 토큰; JS에서 읽을 수 있도록 HttpOnly=false)
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            // 권한 거부는 감사 기록 후 403 JSON
            .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler))
            .addFilterBefore(ipBlockFilter, WebAsyncManagerIntegrationFilter.class)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .httpBasic(basic -> {});
        return http.build();
    }
}
