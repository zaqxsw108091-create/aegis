package com.aegis.dashboard;

import com.aegis.config.AegisSecurityProperties;
import com.aegis.dashboard.dto.DashboardStats;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 관리자용 대시보드 웹 화면(Thymeleaf, 서버 렌더링).
 * 경로가 /admin/** 라 SecurityConfig 에서 ROLE_ADMIN 으로 보호된다.
 */
@Controller
public class DashboardViewController {

    private final DashboardService dashboardService;
    private final AegisSecurityProperties securityProperties;

    public DashboardViewController(DashboardService dashboardService, AegisSecurityProperties securityProperties) {
        this.dashboardService = dashboardService;
        this.securityProperties = securityProperties;
    }

    /** 조치 결과 메시지 코드 → 화면 문구. 코드 외 값은 무시한다(자유 텍스트 반영 금지). */
    private static final Map<String, String> MESSAGES = Map.of(
            "blocked", "IP를 차단했습니다.",
            "unblocked", "IP 차단을 해제했습니다.",
            "unlocked", "계정 잠금을 해제하고 실패 횟수를 초기화했습니다.",
            "self_block", "현재 접속 중인 본인 IP는 차단할 수 없습니다(스스로 잠기는 것을 방지).",
            "block_rejected", "차단할 수 없습니다(화이트리스트 IP이거나 이미 차단 중).",
            "notfound", "대상을 찾지 못했습니다(이미 해제됐거나 존재하지 않음).",
            "invalid", "입력값이 올바르지 않습니다.");

    /**
     * 이벤트 유형 → 처음 보는 사람도 이해할 수 있는 한 줄 설명.
     * 최근 이벤트 표에서 "상세"를 펼치면 이 설명이 함께 보인다.
     */
    private static final Map<String, String> EVENT_EXPLANATIONS = Map.ofEntries(
            Map.entry("LOGIN_SUCCESS", "아이디/비밀번호가 맞아 로그인에 성공했습니다. 정상 동작입니다."),
            Map.entry("LOGIN_FAILURE", "아이디 또는 비밀번호가 틀렸습니다. 한 계정에서 일정 횟수 이상 쌓이면 그 계정이 자동으로 잠깁니다."),
            Map.entry("IP_BLOCKED", "같은 IP에서 실패가 임계치를 넘어 자동으로 차단됐거나, 관리자가 수동으로 차단했습니다. 그 IP의 모든 요청이 일정 시간 거부됩니다."),
            Map.entry("RATE_LIMITED", "같은 IP가 1분 안에 너무 많은 요청을 보내 일시적으로 거부됐습니다. 과도한 요청(DoS성 트래픽)을 막는 장치입니다."),
            Map.entry("ACCESS_DENIED", "권한이 없는 자원에 접근하려다 거부됐습니다(예: 일반 사용자가 관리자 화면 접근 시도)."),
            Map.entry("TOKEN_REUSE", "이미 한 번 사용된 refresh 토큰이 다시 들어왔습니다. 토큰이 탈취된 것으로 보고 해당 사용자의 모든 로그인 세션을 즉시 폐기했습니다."),
            Map.entry("LOGOUT", "사용자가 로그아웃하며 토큰을 스스로 폐기했습니다."),
            Map.entry("IP_UNBLOCKED", "관리자가 차단돼 있던 IP를 수동으로 풀어줬습니다."),
            Map.entry("ACCOUNT_UNLOCKED", "관리자가 잠겨 있던 계정을 수동으로 풀고 실패 횟수를 0으로 되돌렸습니다."));

    @GetMapping("/admin/dashboard")
    public String dashboard(@RequestParam(required = false) String msg, Model model) {
        DashboardStats stats = dashboardService.stats();
        AegisSecurityProperties.Lockout lockout = securityProperties.lockout();
        AegisSecurityProperties.Bruteforce bruteforce = securityProperties.bruteforce();
        model.addAttribute("stats", stats);
        model.addAttribute("events", dashboardService.recentEvents(50));
        model.addAttribute("blockedIps", dashboardService.activeBlockedIps());
        model.addAttribute("users", dashboardService.users());
        model.addAttribute("message", msg == null ? null : MESSAGES.get(msg));
        model.addAttribute("threatLevel", threatLevel(stats));
        model.addAttribute("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        model.addAttribute("eventExplanations", EVENT_EXPLANATIONS);
        model.addAttribute("lockoutMaxAttempts", lockout.maxFailedAttempts());
        model.addAttribute("lockMinutes", lockout.lockMinutes());
        model.addAttribute("ipFailThreshold", bruteforce.ipFailThreshold());
        model.addAttribute("ipBlockMinutes", bruteforce.ipBlockMinutes());
        model.addAttribute("rateLimitPerMinute", securityProperties.rateLimit().requestsPerMinute());
        return "dashboard";
    }

    /**
     * 화면 상단 상태 배너용 위협 수준.
     *  - danger: 현재 차단 중인 IP가 있음(공격 진행/직후)
     *  - warn  : 24시간 내 로그인 실패가 있음(정찰/시도 징후)
     *  - ok    : 특이사항 없음
     */
    static String threatLevel(DashboardStats stats) {
        if (stats.activeBlockedIps() > 0) {
            return "danger";
        }
        if (stats.loginFailuresLast24h() > 0) {
            return "warn";
        }
        return "ok";
    }
}
