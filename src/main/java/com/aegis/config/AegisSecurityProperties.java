package com.aegis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * aegis.security.* 설정 바인딩 (타입 안전).
 * 값이 없으면 CLAUDE.md 기본값을 사용한다.
 */
@ConfigurationProperties(prefix = "aegis.security")
public record AegisSecurityProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue Lockout lockout,
        @DefaultValue Bruteforce bruteforce,
        @DefaultValue RateLimit rateLimit,
        // 차단/레이트리밋 면제 IP(정확 일치)
        @DefaultValue List<String> whitelist
) {
    /**
     * @param secret                  HMAC 서명 키(>=32바이트). 운영은 환경변수로 주입.
     * @param expirationMinutes       Access 토큰 만료(분)
     * @param refreshExpirationMinutes Refresh 토큰 만료(분)
     */
    public record Jwt(
            @DefaultValue("dev-only-change-me-in-production-32bytes-min") String secret,
            @DefaultValue("30") long expirationMinutes,
            @DefaultValue("10080") long refreshExpirationMinutes
    ) {}

    /**
     * @param maxFailedAttempts 계정 잠금 임계 실패 횟수
     * @param lockMinutes       잠금 유지 시간(분)
     */
    public record Lockout(
            @DefaultValue("5") int maxFailedAttempts,
            @DefaultValue("15") int lockMinutes
    ) {}

    public record Bruteforce(
            @DefaultValue("10") int ipFailThreshold,
            @DefaultValue("10") int ipBlockMinutes,
            @DefaultValue("10") int ipFailWindowMinutes
    ) {}

    public record RateLimit(
            @DefaultValue("60") int requestsPerMinute
    ) {}
}
