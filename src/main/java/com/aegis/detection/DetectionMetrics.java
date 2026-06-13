package com.aegis.detection;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 탐지/차단 메트릭(Micrometer). /actuator/prometheus 로 노출된다.
 *  - aegis_detection_login_failures_total
 *  - aegis_detection_ip_blocked_total
 *  - aegis_detection_rate_limited_total
 *
 * 생성 시 카운터를 등록해 두므로 값이 0이어도 스크레이프에 노출된다.
 */
@Component
public class DetectionMetrics {

    private final Counter loginFailures;
    private final Counter ipBlocked;
    private final Counter rateLimited;

    public DetectionMetrics(MeterRegistry registry) {
        this.loginFailures = Counter.builder("aegis.detection.login_failures")
                .description("로그인/인증 실패 횟수").register(registry);
        this.ipBlocked = Counter.builder("aegis.detection.ip_blocked")
                .description("무차별 대입 탐지로 IP 차단된 횟수").register(registry);
        this.rateLimited = Counter.builder("aegis.detection.rate_limited")
                .description("레이트 리미트 초과로 거부된 횟수").register(registry);
    }

    public void loginFailure() {
        loginFailures.increment();
    }

    public void ipBlocked() {
        ipBlocked.increment();
    }

    public void rateLimited() {
        rateLimited.increment();
    }
}
