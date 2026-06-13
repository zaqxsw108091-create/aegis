package com.aegis.detection;

/**
 * 레이트 리미터 추상화.
 *
 * <p>현재는 인메모리(Bucket4j) 구현({@link Bucket4jRateLimiter})만 있으나,
 * 분산 환경에서는 동일 인터페이스로 Redis/Hazelcast 기반 구현으로 교체할 수 있다.
 */
public interface RateLimiter {

    /**
     * 주어진 키(보통 클라이언트 IP)에 대해 토큰 1개 소비를 시도한다.
     *
     * @return 허용되면 true, 한도 초과면 false
     */
    boolean tryConsume(String key);
}
