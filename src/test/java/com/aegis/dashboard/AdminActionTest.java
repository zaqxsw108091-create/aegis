package com.aegis.dashboard;

import com.aegis.audit.AuditEventType;
import com.aegis.audit.AuditLogRepository;
import com.aegis.auth.Role;
import com.aegis.auth.User;
import com.aegis.auth.UserRepository;
import com.aegis.detection.BlockedIpRepository;
import com.aegis.detection.BruteForceProtectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 수동 조치: IP 수동 차단/해제, 계정 잠금 해제, 사용자 현황 API.
 * 상태 변경은 /admin/** (CSRF 보호) 에서만 가능해야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminActionTest {

    private static final String IP = "203.0.113.77";

    @Autowired private MockMvc mvc;
    @Autowired private BlockedIpRepository blockedIpRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BruteForceProtectionService protection;

    @BeforeEach
    void clean() {
        blockedIpRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "USER")
    void 비관리자는_수동차단을_할_수_없다_403() throws Exception {
        mvc.perform(post("/admin/blocked-ips").with(csrf()).param("ip", IP))
                .andExpect(status().isForbidden());
        assertThat(protection.isBlocked(IP)).isFalse();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void CSRF_토큰_없는_관리자_조치는_403() throws Exception {
        // 대시보드는 Basic 인증(브라우저 자동 전송)으로 쓰이므로 상태 변경엔 CSRF 토큰이 필수
        mvc.perform(post("/admin/blocked-ips").param("ip", IP))
                .andExpect(status().isForbidden());
        assertThat(protection.isBlocked(IP)).isFalse();
    }

    @Test
    @WithMockUser(username = "daeyoung0", roles = "ADMIN")
    void 관리자_수동차단_후_해제() throws Exception {
        // 차단
        mvc.perform(post("/admin/blocked-ips").with(csrf())
                        .param("ip", IP).param("minutes", "30").param("reason", "의심 트래픽"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard?msg=blocked"));
        assertThat(protection.isBlocked(IP)).isTrue();
        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> l.getType() == AuditEventType.IP_BLOCKED && "daeyoung0".equals(l.getActor()));

        // 이미 차단 중이면 거부
        mvc.perform(post("/admin/blocked-ips").with(csrf()).param("ip", IP))
                .andExpect(redirectedUrl("/admin/dashboard?msg=block_rejected"));

        // 해제
        mvc.perform(post("/admin/blocked-ips/unblock").with(csrf()).param("ip", IP))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard?msg=unblocked"));
        assertThat(protection.isBlocked(IP)).isFalse();
        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> l.getType() == AuditEventType.IP_UNBLOCKED && "daeyoung0".equals(l.getActor()));

        // 이미 해제된 IP 재해제 → notfound
        mvc.perform(post("/admin/blocked-ips/unblock").with(csrf()).param("ip", IP))
                .andExpect(redirectedUrl("/admin/dashboard?msg=notfound"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 잘못된_IP_형식은_거부된다() throws Exception {
        mvc.perform(post("/admin/blocked-ips").with(csrf()).param("ip", "not an ip!"))
                .andExpect(redirectedUrl("/admin/dashboard?msg=invalid"));
        mvc.perform(post("/admin/blocked-ips").with(csrf()).param("ip", IP).param("minutes", "0"))
                .andExpect(redirectedUrl("/admin/dashboard?msg=invalid"));
        assertThat(blockedIpRepository.count()).isZero();
    }

    @Test
    void 해제_후에는_실패_집계가_다시_시작된다() {
        // 임계치(10)만큼 실패 → 자동 차단
        for (int i = 0; i < 10; i++) {
            protection.onLoginFailure(IP, "ghost" + i);
        }
        assertThat(protection.isBlocked(IP)).isTrue();

        // 관리자 해제
        assertThat(protection.unblock(IP, "admin")).isTrue();
        assertThat(protection.isBlocked(IP)).isFalse();

        // 해제 직후 실패 1건으로는 재차단되지 않아야 한다(옛 실패는 더 이상 세지 않음)
        protection.onLoginFailure(IP, "ghost-after");
        assertThat(protection.isBlocked(IP)).isFalse();

        // 해제 이후 다시 임계치를 채우면 재차단
        for (int i = 0; i < 9; i++) {
            protection.onLoginFailure(IP, "ghost-again" + i);
        }
        assertThat(protection.isBlocked(IP)).isTrue();
    }

    @Test
    @WithMockUser(username = "daeyoung0", roles = "ADMIN")
    void 관리자_계정잠금해제_및_실패횟수_초기화() throws Exception {
        User u = userRepository.save(new User("lockedguy", "hash", Role.USER));
        u.setFailedLoginCount(5);
        u.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        userRepository.save(u);

        // 사용자 현황 API 에 잠금 상태가 보인다
        mvc.perform(get("/api/admin/dashboard/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("lockedguy"))
                .andExpect(jsonPath("$[0].failedLoginCount").value(5))
                .andExpect(jsonPath("$[0].locked").value(true));

        // 잠금 해제
        mvc.perform(post("/admin/users/unlock").with(csrf()).param("username", "lockedguy"))
                .andExpect(redirectedUrl("/admin/dashboard?msg=unlocked"));

        User after = userRepository.findByUsername("lockedguy").orElseThrow();
        assertThat(after.getFailedLoginCount()).isZero();
        assertThat(after.getLockedUntil()).isNull();
        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> l.getType() == AuditEventType.ACCOUNT_UNLOCKED && "daeyoung0".equals(l.getActor()));

        // 없는 사용자 → notfound
        mvc.perform(post("/admin/users/unlock").with(csrf()).param("username", "nobody"))
                .andExpect(redirectedUrl("/admin/dashboard?msg=notfound"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void 비관리자는_사용자현황을_볼_수_없다() throws Exception {
        mvc.perform(get("/api/admin/dashboard/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 대시보드_폼에는_CSRF_토큰이_자동_삽입되고_인라인_스타일이_없다() throws Exception {
        blockedIpRepository.save(new com.aegis.detection.BlockedIp(IP, "test",
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(10)));
        mvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/admin/blocked-ips/unblock")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("사용자 현황")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(" style="))));
    }
}
