package com.aegis.detection;

import com.aegis.config.AegisSecurityProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bucket4j 기반 인메모리 레이트 리미터(단일 인스턴스용).
 * 키(IP)별 토큰 버킷으로 분당 허용량을 관리한다.
 *
 * <p>분산 환경에서는 이 빈을 분산 백엔드 구현으로 교체하면 된다({@link RateLimiter}).
 */
@Service
public class Bucket4jRateLimiter implements RateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int permitsPerMinute;

    public Bucket4jRateLimiter(AegisSecurityProperties props) {
        this.permitsPerMinute = props.rateLimit().requestsPerMinute();
    }

    @Override
    public boolean tryConsume(String key) {
        return resolveBucket(key).tryConsume(1);
    }

    private Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.builder()
                    .capacity(permitsPerMinute)
                    .refillGreedy(permitsPerMinute, Duration.ofMinutes(1))
                    .build();
            return Bucket.builder().addLimit(limit).build();
        });
    }
}
