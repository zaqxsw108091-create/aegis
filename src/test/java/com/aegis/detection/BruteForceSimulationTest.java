package com.aegis.detection;

import com.aegis.audit.SecurityEventRepository;
import com.aegis.audit.SecurityEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 무차별 대입 시뮬레이션: 같은 IP에서 로그인(인증) 실패를 반복하면
 * 임계치 도달 시 자동 차단되고, 이후 요청은 403으로 막히는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BruteForceSimulationTest {

    private static final String ATTACKER_IP = "203.0.113.50";
    private static final String OTHER_IP = "203.0.113.99";
    private static final int THRESHOLD = 10; // aegis.security.bruteforce.ip-fail-threshold 기본값

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BlockedIpRepository blockedIpRepository;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @BeforeEach
    void clean() {
        blockedIpRepository.deleteAll();
        securityEventRepository.deleteAll();
    }

    private RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void 반복된_로그인_실패는_IP를_자동차단하고_이후_요청은_403() throws Exception {
        // 1) 임계치만큼 잘못된 자격증명으로 인증 시도 → 매번 401
        for (int i = 0; i < THRESHOLD; i++) {
            mvc.perform(get("/actuator/health")
                            .with(httpBasic("attacker", "wrong-password-" + i))
                            .with(fromIp(ATTACKER_IP)))
                    .andExpect(status().isUnauthorized());
        }

        // 2) 차단 IP 목록에 등록되고, IP_BLOCKED 감사 이벤트가 DB에 기록된다
        assertThat(blockedIpRepository.existsByIpAndBlockedUntilAfter(ATTACKER_IP, LocalDateTime.now()))
                .as("임계치 초과 후 공격자 IP가 차단되어야 한다")
                .isTrue();
        assertThat(securityEventRepository.countByTypeAndIpAndCreatedAtAfter(
                SecurityEventType.IP_BLOCKED, ATTACKER_IP, LocalDateTime.now().minusMinutes(1)))
                .as("IP_BLOCKED 이벤트가 DB에 기록되어야 한다")
                .isGreaterThan(0);

        // 3) 차단된 IP의 이후 요청은 공개 엔드포인트(/health)라도 403
        mvc.perform(get("/health").with(fromIp(ATTACKER_IP)))
                .andExpect(status().isForbidden());

        // 4) 다른 IP는 영향받지 않는다
        mvc.perform(get("/health").with(fromIp(OTHER_IP)))
                .andExpect(status().isOk());
    }
}
