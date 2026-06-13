package com.aegis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * aegis.security.* 설정 바인딩 (타입 안전).
 * 값이 없으면 CLAUDE.md 기본값을 사용한다.
 */
@ConfigurationProperties(prefix = "aegis.security")
public record AegisSecurityProperties(
        @DefaultValue Bruteforce bruteforce,
        @DefaultValue RateLimit rateLimit
) {
    /**
     * @param ipFailThreshold     차단 임계치(이 횟수 이상 실패 시 차단)
     * @param ipBlockMinutes      차단 유지 시간(분)
     * @param ipFailWindowMinutes 실패 집계 시간창(분)
     */
    public record Bruteforce(
            @DefaultValue("10") int ipFailThreshold,
            @DefaultValue("10") int ipBlockMinutes,
            @DefaultValue("10") int ipFailWindowMinutes
    ) {}

    /**
     * @param requestsPerMinute IP당 분당 허용 요청 수
     */
    public record RateLimit(
            @DefaultValue("60") int requestsPerMinute
    ) {}
}
