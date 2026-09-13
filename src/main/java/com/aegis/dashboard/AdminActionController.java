package com.aegis.dashboard;

import com.aegis.audit.AuditEventType;
import com.aegis.audit.AuditResult;
import com.aegis.audit.AuditService;
import com.aegis.auth.LoginAttemptService;
import com.aegis.detection.BruteForceProtectionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.regex.Pattern;

/**
 * 관리자 수동 조치(상태 변경): IP 수동 차단 / IP 차단 해제 / 계정 잠금 해제.
 *
 * <p>의도적으로 /api/** 가 아닌 /admin/** 에 둔다. 대시보드는 브라우저(Basic 인증)로 쓰이므로
 * 브라우저가 자격증명을 자동 전송한다 → CSRF 방어가 필요하다. /admin/** 는 CSRF 보호 대상이고
 * Thymeleaf 폼(th:action)이 토큰을 자동 삽입한다. (/api/** 는 헤더 토큰(JWT) 전용이라 CSRF 예외)
 *
 * <p>결과 메시지는 세션 없이 전달하기 위해 고정 코드(query param)만 사용한다(자유 텍스트 반영 금지).
 */
@Controller
public class AdminActionController {

    /** IPv4/IPv6 및 호스트명 형태만 허용(공백/제어문자 차단). 화이트리스트 검증. */
    private static final Pattern IP_PATTERN = Pattern.compile("^[0-9A-Fa-f:.]{1,64}$");
    private static final int MAX_BLOCK_MINUTES = 60 * 24 * 7; // 7일
    private static final int MAX_REASON_LENGTH = 200;

    private final BruteForceProtectionService protection;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;

    public AdminActionController(BruteForceProtectionService protection,
                                 LoginAttemptService loginAttemptService,
                                 AuditService auditService) {
        this.protection = protection;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
    }

    @PostMapping("/admin/blocked-ips")
    public String blockIp(@RequestParam String ip,
                          @RequestParam(defaultValue = "10") int minutes,
                          @RequestParam(defaultValue = "") String reason,
                          Authentication auth) {
        String target = ip == null ? "" : ip.trim();
        if (!IP_PATTERN.matcher(target).matches() || minutes < 1 || minutes > MAX_BLOCK_MINUTES) {
            return redirect("invalid");
        }
        String why = reason.isBlank() ? "관리자 수동 차단" : reason.trim();
        if (why.length() > MAX_REASON_LENGTH) {
            why = why.substring(0, MAX_REASON_LENGTH);
        }
        boolean done = protection.blockManually(target, minutes, why, actor(auth));
        return redirect(done ? "blocked" : "block_rejected");
    }

    @PostMapping("/admin/blocked-ips/unblock")
    public String unblockIp(@RequestParam String ip, Authentication auth) {
        String target = ip == null ? "" : ip.trim();
        if (!IP_PATTERN.matcher(target).matches()) {
            return redirect("invalid");
        }
        boolean done = protection.unblock(target, actor(auth));
        return redirect(done ? "unblocked" : "notfound");
    }

    @PostMapping("/admin/users/unlock")
    public String unlockUser(@RequestParam String username, Authentication auth, HttpServletRequest request) {
        String target = username == null ? "" : username.trim();
        if (target.isEmpty() || target.length() > 50) {
            return redirect("invalid");
        }
        boolean done = loginAttemptService.unlock(target);
        if (done) {
            auditService.record(AuditEventType.ACCOUNT_UNLOCKED, AuditResult.SUCCESS, actor(auth),
                    request.getRemoteAddr(), "username=" + target + " 잠금 해제/실패 카운트 초기화");
        }
        return redirect(done ? "unlocked" : "notfound");
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "anonymous";
    }

    private static String redirect(String code) {
        return "redirect:/admin/dashboard?msg=" + code;
    }
}
