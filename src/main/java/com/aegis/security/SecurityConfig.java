package com.aegis.security;

import com.aegis.audit.SecurityEventService;
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

/**
 * 보안 설정.
 * P2: 인증/JWT, P4: 보안 헤더·CSRF 강화 예정.
 *
 * 공개: 커스텀 헬스체크(/health), OpenAPI 문서.
 * 인증 필요: Actuator 및 그 외 모든 요청.
 *
 * 탐지 필터 순서(앞쪽일수록 먼저 실행):
 *   1) IpBlockFilter   - 차단된 IP는 즉시 403
 *   2) RateLimitFilter - IP당 분당 한도 초과 시 429
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 인증 실패 이벤트(AbstractAuthenticationFailureEvent)를 발행하도록 명시적으로 등록한다.
     * 이 빈이 있어야 무차별 대입 탐지 리스너가 실패를 수신한다.
     */
    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher delegate) {
        return new DefaultAuthenticationEventPublisher(delegate);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           BruteForceProtectionService protection,
                                           RateLimitService rateLimitService,
                                           SecurityEventService securityEventService,
                                           ObjectMapper objectMapper) throws Exception {
        IpBlockFilter ipBlockFilter = new IpBlockFilter(protection, objectMapper);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimitService, securityEventService, objectMapper);

        http
            .authorizeHttpRequests(auth -> auth
                // 공개 엔드포인트
                .requestMatchers("/health").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Actuator(health/info/prometheus)는 인증 필요
                .requestMatchers("/actuator/**").authenticated()
                .anyRequest().authenticated()
            )
            // IpBlockFilter를 가장 앞단(표준 필터 WebAsyncManagerIntegrationFilter 이전)에 둔다.
            .addFilterBefore(ipBlockFilter, WebAsyncManagerIntegrationFilter.class)
            // RateLimitFilter는 그 다음, 인증 처리 전에 둔다.
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .httpBasic(basic -> {});
        return http.build();
    }
}
