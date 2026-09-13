package com.aegis.dashboard;

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

    public DashboardViewController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 조치 결과 메시지 코드 → 화면 문구. 코드 외 값은 무시한다(자유 텍스트 반영 금지). */
    private static final Map<String, String> MESSAGES = Map.of(
            "blocked", "IP를 차단했습니다.",
            "unblocked", "IP 차단을 해제했습니다.",
            "unlocked", "계정 잠금을 해제하고 실패 횟수를 초기화했습니다.",
            "block_rejected", "차단할 수 없습니다(화이트리스트 IP이거나 이미 차단 중).",
            "notfound", "대상을 찾지 못했습니다(이미 해제됐거나 존재하지 않음).",
            "invalid", "입력값이 올바르지 않습니다.");

    @GetMapping("/admin/dashboard")
    public String dashboard(@RequestParam(required = false) String msg, Model model) {
        DashboardStats stats = dashboardService.stats();
        model.addAttribute("stats", stats);
        model.addAttribute("events", dashboardService.recentEvents(50));
        model.addAttribute("blockedIps", dashboardService.activeBlockedIps());
        model.addAttribute("users", dashboardService.users());
        model.addAttribute("message", msg == null ? null : MESSAGES.get(msg));
        model.addAttribute("threatLevel", threatLevel(stats));
        model.addAttribute("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
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
