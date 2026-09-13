package com.aegis.dashboard;

import com.aegis.dashboard.dto.DashboardStats;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        DashboardStats stats = dashboardService.stats();
        model.addAttribute("stats", stats);
        model.addAttribute("events", dashboardService.recentEvents(50));
        model.addAttribute("blockedIps", dashboardService.activeBlockedIps());
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
