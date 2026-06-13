package com.aegis.detection;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 레이트 리미팅: 같은 IP가 분당 한도(기본 60)를 넘기면 429로 거부되는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitFilterTest {

    // 다른 테스트와 버킷이 겹치지 않도록 전용 IP 사용
    private static final String IP = "198.51.100.7";
    private static final int LIMIT = 60; // aegis.security.rate-limit.requests-per-minute 기본값

    @Autowired
    private MockMvc mvc;

    private RequestPostProcessor fromIp() {
        return request -> {
            request.setRemoteAddr(IP);
            return request;
        };
    }

    @Test
    void 분당_한도까지는_통과하고_초과하면_429() throws Exception {
        // 한도까지는 모두 200 (공개 /health)
        for (int i = 0; i < LIMIT; i++) {
            mvc.perform(get("/health").with(fromIp()))
                    .andExpect(status().isOk());
        }
        // 한도 초과 요청은 429
        mvc.perform(get("/health").with(fromIp()))
                .andExpect(status().isTooManyRequests());
    }
}
