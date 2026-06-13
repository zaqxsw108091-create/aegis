package com.aegis.detection;

import com.aegis.audit.AuditLogRepository;
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
 * 화이트리스트(예외 IP)는 무차별 대입 차단과 레이트 리미팅에서 면제되는지 검증한다.
 */
@SpringBootTest(properties = "aegis.security.whitelist[0]=198.51.100.200")
@AutoConfigureMockMvc
class WhitelistTest {

    private static final String WHITELISTED_IP = "198.51.100.200";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BlockedIpRepository blockedIpRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void clean() {
        blockedIpRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    private RequestPostProcessor fromWhitelisted() {
        return request -> {
            request.setRemoteAddr(WHITELISTED_IP);
            return request;
        };
    }

    @Test
    void 화이트리스트_IP는_반복_실패해도_차단되지_않는다() throws Exception {
        // 임계치(10)를 훨씬 넘는 15회 실패에도 차단되지 않아야 한다.
        for (int i = 0; i < 15; i++) {
            mvc.perform(get("/actuator/health")
                            .with(httpBasic("attacker", "wrong-" + i))
                            .with(fromWhitelisted()))
                    .andExpect(status().isUnauthorized());
        }

        assertThat(blockedIpRepository.existsByIpAndBlockedUntilAfter(WHITELISTED_IP, LocalDateTime.now()))
                .as("화이트리스트 IP는 차단되지 않아야 한다")
                .isFalse();

        // 공개 엔드포인트도 정상 통과(403 아님)
        mvc.perform(get("/health").with(fromWhitelisted()))
                .andExpect(status().isOk());
    }

    @Test
    void 화이트리스트_IP는_레이트리밋_면제된다() throws Exception {
        // 분당 한도(60)를 초과하는 70회 요청에도 모두 통과(429 없음).
        for (int i = 0; i < 70; i++) {
            mvc.perform(get("/health").with(fromWhitelisted()))
                    .andExpect(status().isOk());
        }
    }
}
