package com.aegis.detection;

import com.aegis.config.AegisSecurityProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP당 요청 레이트 리미팅 (Bucket4j 인메모리).
 * 분당 허용량을 토큰 버킷으로 관리한다.
 */
@Service
public class RateLimitService {

    /** 거부 이벤트를 DB에 기록하는 IP별 최소 간격(자기-DoS 방지). */
    private static final long REJECTION_RECORD_COOLDOWN_MS = 60_000L;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRejectionRecordedAt = new ConcurrentHashMap<>();
    private final int requestsPerMinute;

    public RateLimitService(AegisSecurityProperties props) {
        this.requestsPerMinute = props.rateLimit().requestsPerMinute();
    }

    /** 토큰 1개 소비 시도. 한도 초과면 false. */
    public boolean tryConsume(String ip) {
        return resolveBucket(ip).tryConsume(1);
    }

    private Bucket resolveBucket(String ip) {
        return buckets.computeIfAbsent(ip, key -> {
            Bandwidth limit = Bandwidth.builder()
                    .capacity(requestsPerMinute)
                    .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                    .build();
            return Bucket.builder().addLimit(limit).build();
        });
    }

    /**
     * 거부를 DB에 기록할지 여부. 폭주 시 동일 IP의 거부 로그가 DB를 가득 채우지 않도록
     * IP당 쿨다운(1분)으로 1회만 기록하게 한다(콘솔 경고는 매번 남긴다).
     */
    public boolean shouldRecordRejection(String ip) {
        long now = System.currentTimeMillis();
        Long last = lastRejectionRecordedAt.get(ip);
        if (last == null || now - last > REJECTION_RECORD_COOLDOWN_MS) {
            lastRejectionRecordedAt.put(ip, now);
            return true;
        }
        return false;
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }
}
