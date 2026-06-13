package com.aegis.audit;

import com.aegis.detection.BlockedIpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 이벤트가 AuditLog(시각/IP/이벤트유형/결과)에 적재되는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditLogTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private BlockedIpRepository blockedIpRepository;

    @BeforeEach
    void clean() {
        auditLogRepository.deleteAll();
        blockedIpRepository.deleteAll();
    }

    private RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void 로그인_실패는_감사로그에_적재된다() throws Exception {
        String ip = "192.0.2.10";

        mvc.perform(get("/actuator/health")
                        .with(httpBasic("ghost", "bad-credentials"))
                        .with(fromIp(ip)))
                .andExpect(status().isUnauthorized());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLog row = logs.get(0);
        assertThat(row.getType()).isEqualTo(AuditEventType.LOGIN_FAILURE);
        assertThat(row.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(row.getIp()).isEqualTo(ip);
        assertThat(row.getCreatedAt()).isNotNull();
    }

    @Test
    void CSRF_토큰_없는_상태변경_요청은_403이고_권한거부가_감사로그에_적재된다() throws Exception {
        // 인증된 사용자라도 CSRF 토큰이 없으면 거부된다(= CSRF 활성화 확인)
        mvc.perform(post("/health").with(user("tester")))
                .andExpect(status().isForbidden());

        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> l.getType() == AuditEventType.ACCESS_DENIED
                        && l.getResult() == AuditResult.DENIED);
    }
}
