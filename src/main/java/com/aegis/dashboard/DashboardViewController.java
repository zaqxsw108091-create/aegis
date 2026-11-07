package com.aegis.dashboard;

import com.aegis.config.AegisSecurityProperties;
import com.aegis.dashboard.dto.AuditEventView;
import com.aegis.dashboard.dto.DashboardStats;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    /** 한 번에 보여줄 "행위자/IP 상세" 드릴다운 이벤트 최대 개수. */
    private static final int DETAIL_EVENT_LIMIT = 200;
    /** 드릴다운 쿼리 파라미터(actor/ip)로 들어온 값의 최대 허용 길이(남용 방지용 방어적 상한). */
    private static final int DETAIL_PARAM_MAX_LEN = 100;

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
        model.addAttribute("eventExplanations", EventExplanations.MAP);
        model.addAttribute("lockoutMaxAttempts", lockout.maxFailedAttempts());
        model.addAttribute("lockMinutes", lockout.lockMinutes());
        model.addAttribute("ipFailThreshold", bruteforce.ipFailThreshold());
        model.addAttribute("ipBlockMinutes", bruteforce.ipBlockMinutes());
        model.addAttribute("rateLimitPerMinute", securityProperties.rateLimit().requestsPerMinute());
        return "dashboard";
    }

    /**
     * 특정 행위자(사용자명 등) 또는 IP의 전체 이력만 모아서 보는 "드릴다운" 화면.
     * 대시보드의 사용자 현황/차단 IP/최근 이벤트 표에서 이름·IP를 클릭하면 여기로 온다.
     * actor/ip 둘 다 없으면(URL을 직접 손댄 경우) 메인 대시보드로 돌려보낸다.
     */
    @GetMapping("/admin/dashboard/detail")
    public String detail(@RequestParam(required = false) String actor,
                         @RequestParam(required = false) String ip,
                         Model model) {
        String a = clean(actor);
        String i = clean(ip);
        if (a == null && i == null) {
            return "redirect:/admin/dashboard";
        }
        boolean byActor = a != null;
        String subjectValue = byActor ? a : i;
        List<AuditEventView> events = byActor
                ? dashboardService.eventsByActor(subjectValue, DETAIL_EVENT_LIMIT)
                : dashboardService.eventsByIp(subjectValue, DETAIL_EVENT_LIMIT);

        model.addAttribute("subjectType", byActor ? "actor" : "ip");
        model.addAttribute("subjectTypeLabel", byActor ? "행위자(사용자)" : "IP");
        model.addAttribute("subjectValue", subjectValue);
        model.addAttribute("events", events);
        model.addAttribute("eventExplanations", EventExplanations.MAP);
        model.addAttribute("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return "dashboard-detail";
    }

    /** 공백만 있거나 너무 긴 쿼리 파라미터는 없는 것으로 취급한다. */
    private static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty() || v.length() > DETAIL_PARAM_MAX_LEN) {
            return null;
        }
        return v;
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
