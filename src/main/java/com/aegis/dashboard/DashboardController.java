package com.aegis.dashboard;

import com.aegis.dashboard.dto.AuditEventView;
import com.aegis.dashboard.dto.BlockedIpView;
import com.aegis.dashboard.dto.DashboardStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자(ROLE_ADMIN) 전용 모니터링 REST API.
 * 경로가 /api/admin/** 라 SecurityConfig 에서 ROLE_ADMIN 으로 보호된다.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@Tag(name = "dashboard", description = "관리자 전용 모니터링 API")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/events")
    @Operation(summary = "최근 보안 이벤트", security = @SecurityRequirement(name = "bearer"))
    public List<AuditEventView> events(@RequestParam(defaultValue = "50") int limit) {
        return dashboardService.recentEvents(limit);
    }

    @GetMapping("/blocked-ips")
    @Operation(summary = "차단 IP 목록(유효)", security = @SecurityRequirement(name = "bearer"))
    public List<BlockedIpView> blockedIps() {
        return dashboardService.activeBlockedIps();
    }

    @GetMapping("/stats")
    @Operation(summary = "요약 통계 + 메트릭", security = @SecurityRequirement(name = "bearer"))
    public DashboardStats stats() {
        return dashboardService.stats();
    }
}
