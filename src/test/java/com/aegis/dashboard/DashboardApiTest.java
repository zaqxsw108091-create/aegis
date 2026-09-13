package com.aegis.dashboard;

import com.aegis.audit.AuditEventType;
import com.aegis.audit.AuditLog;
import com.aegis.audit.AuditLogRepository;
import com.aegis.audit.AuditResult;
import com.aegis.detection.BlockedIp;
import com.aegis.detection.BlockedIpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 대시보드: 비관리자/비인증 접근 차단, 관리자 데이터 정상 노출, 웹 화면 렌더링 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardApiTest {

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

    @Test
    void 비인증_접근은_401() throws Exception {
        mvc.perform(get("/api/admin/dashboard/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void 비관리자_접근은_403() throws Exception {
        mvc.perform(get("/api/admin/dashboard/stats")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard/events")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_데이터를_정상_조회한다() throws Exception {
        auditLogRepository.save(new AuditLog(AuditEventType.LOGIN_FAILURE, AuditResult.FAILURE,
                "mallory", "203.0.113.10", "로그인 실패", LocalDateTime.now()));
        blockedIpRepository.save(new BlockedIp("203.0.113.10", "brute-force",
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(10)));

        mvc.perform(get("/api/admin/dashboard/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("LOGIN_FAILURE"))
                .andExpect(jsonPath("$[0].actor").value("mallory"))
                .andExpect(jsonPath("$[0].ip").value("203.0.113.10"));

        mvc.perform(get("/api/admin/dashboard/blocked-ips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ip").value("203.0.113.10"));

        mvc.perform(get("/api/admin/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginFailuresTotal").value(1))
                .andExpect(jsonPath("$.activeBlockedIps").value(1))
                .andExpect(jsonPath("$.eventCountsByType.LOGIN_FAILURE").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자_웹화면이_렌더링된다() throws Exception {
        mvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                // 위협 수준 배너와 외부 스타일시트 링크가 포함된다
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/dashboard.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SYSTEM SECURE")))
                // CSP(default-src 'self') 준수: 인라인 <style>/style= 속성을 쓰지 않는다
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<style"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(" style="))));
    }

    @Test
    void 대시보드_스타일시트는_인증없이_제공된다() throws Exception {
        // CSS 는 민감정보가 없고, 브라우저가 페이지와 함께 요청하므로 공개 경로여야 한다
        mvc.perform(get("/css/dashboard.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(".status.level-danger")));
    }
}
