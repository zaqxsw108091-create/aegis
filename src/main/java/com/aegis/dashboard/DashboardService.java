package com.aegis.dashboard;

import com.aegis.audit.AuditEventType;
import com.aegis.audit.AuditLog;
import com.aegis.audit.AuditLogRepository;
import com.aegis.dashboard.dto.AuditEventView;
import com.aegis.dashboard.dto.BlockedIpView;
import com.aegis.dashboard.dto.DashboardStats;
import com.aegis.dashboard.dto.MetricsSummary;
import com.aegis.dashboard.dto.UserView;
import com.aegis.auth.UserRepository;
import com.aegis.detection.BlockedIpRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 대시보드 데이터 조회. AuditLog/BlockedIp(DB) 와 Micrometer(메트릭)에서 현황을 모은다.
 */
@Service
public class DashboardService {

    private static final int MAX_EVENTS = 200;

    private final AuditLogRepository auditLogRepository;
    private final BlockedIpRepository blockedIpRepository;
    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;

    public DashboardService(AuditLogRepository auditLogRepository,
                            BlockedIpRepository blockedIpRepository,
                            UserRepository userRepository,
                            MeterRegistry meterRegistry) {
        this.auditLogRepository = auditLogRepository;
        this.blockedIpRepository = blockedIpRepository;
        this.userRepository = userRepository;
        this.meterRegistry = meterRegistry;
    }

    /** 사용자별 로그인 실패 횟수/잠금 상태(관리자 조회용). 잠긴 계정이 먼저 오도록 정렬. */
    @Transactional(readOnly = true)
    public List<UserView> users() {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.findAll().stream()
                .map(u -> new UserView(u.getUsername(), u.getRole().name(), u.isEnabled(),
                        u.getFailedLoginCount(), u.getLockedUntil(), u.isLocked(now)))
                .sorted(Comparator.comparing(UserView::locked).reversed()
                        .thenComparing(UserView::failedLoginCount, Comparator.reverseOrder())
                        .thenComparing(UserView::username))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> recentEvents(int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_EVENTS);
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, capped))
                .stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<BlockedIpView> activeBlockedIps() {
        return blockedIpRepository.findByBlockedUntilAfterOrderByBlockedUntilDesc(LocalDateTime.now())
                .stream()
                .map(b -> new BlockedIpView(b.getIp(), b.getReason(), b.getBlockedAt(), b.getBlockedUntil()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardStats stats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayAgo = now.minusHours(24);

        Map<String, Long> byType = new LinkedHashMap<>();
        for (AuditEventType type : AuditEventType.values()) {
            byType.put(type.name(), auditLogRepository.countByType(type));
        }

        MetricsSummary metrics = new MetricsSummary(
                counterValue("aegis.detection.login_failures"),
                counterValue("aegis.detection.ip_blocked"),
                counterValue("aegis.detection.rate_limited"));

        return new DashboardStats(
                auditLogRepository.count(),
                auditLogRepository.countByType(AuditEventType.LOGIN_FAILURE),
                auditLogRepository.countByTypeAndCreatedAtAfter(AuditEventType.LOGIN_FAILURE, dayAgo),
                blockedIpRepository.countByBlockedUntilAfter(now),
                byType,
                metrics);
    }

    private double counterValue(String name) {
        Counter counter = meterRegistry.find(name).counter();
        return counter == null ? 0d : counter.count();
    }

    private AuditEventView toView(AuditLog a) {
        return new AuditEventView(a.getCreatedAt(), a.getType().name(), a.getResult().name(),
                a.getActor(), a.getIp(), a.getDetail());
    }
}
