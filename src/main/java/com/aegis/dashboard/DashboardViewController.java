package com.aegis.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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
        model.addAttribute("stats", dashboardService.stats());
        model.addAttribute("events", dashboardService.recentEvents(50));
        model.addAttribute("blockedIps", dashboardService.activeBlockedIps());
        return "dashboard";
    }
}
