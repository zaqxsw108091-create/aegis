package com.aegis.detection;

import com.aegis.alert.AlertService;
import com.aegis.audit.AuditEventType;
import com.aegis.audit.AuditResult;
import com.aegis.audit.AuditService;
import com.aegis.config.AegisSecurityProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 무차별 대입(brute-force) 탐지/차단.
 * 로그인 실패를 IP 단위로 집계하고, 설정한 시간창 안에서 임계치 이상이면 해당 IP를 자동 차단한다.
 */
@Service
public class BruteForceProtectionService {

    private final AuditService events;
    private final BlockedIpRepository blockedIpRepository;
    private final IpWhitelist whitelist;
    private final DetectionMetrics metrics;
    private final AlertService alertService;
    private final AegisSecurityProperties props;

    public BruteForceProtectionService(AuditService events,
                                       BlockedIpRepository blockedIpRepository,
                                       IpWhitelist whitelist,
                                       DetectionMetrics metrics,
                                       AlertService alertService,
                                       AegisSecurityProperties props) {
        this.events = events;
        this.blockedIpRepository = blockedIpRepository;
        this.whitelist = whitelist;
        this.metrics = metrics;
        this.alertService = alertService;
        this.props = props;
    }

    /** 로그인(인증) 실패 1건을 기록하고, 임계치를 넘으면 IP를 차단한다. */
    @Transactional
    public void onLoginFailure(String ip, String username) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        events.record(AuditEventType.LOGIN_FAILURE, AuditResult.FAILURE, safe(username), ip, "로그인 실패");
        metrics.loginFailure();

        // 화이트리스트 IP는 집계는 하되 차단하지 않는다.
        if (whitelist.isWhitelisted(ip)) {
            return;
        }

        AegisSecurityProperties.Bruteforce bf = props.bruteforce();
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(bf.ipFailWindowMinutes());
        // 관리자가 수동 해제한 뒤에는 그 시점부터 다시 센다(해제 직후 재차단 방지).
        LocalDateTime effectiveStart = events.lastEventAt(AuditEventType.IP_UNBLOCKED, ip)
                .filter(t -> t.isAfter(windowStart))
                .orElse(windowStart);
        long recentFailures = events.countSince(AuditEventType.LOGIN_FAILURE, ip, effectiveStart);

        if (recentFailures >= bf.ipFailThreshold() && !isBlocked(ip)) {
            block(ip, "brute-force 탐지: " + bf.ipFailWindowMinutes() + "분 내 실패 " + recentFailures + "회",
                    "system", props.bruteforce().ipBlockMinutes(), true);
        }
    }

    /** 현재 차단 상태 여부. 화이트리스트 IP는 항상 false. */
    @Transactional(readOnly = true)
    public boolean isBlocked(String ip) {
        if (ip == null || ip.isBlank() || whitelist.isWhitelisted(ip)) {
            return false;
        }
        return blockedIpRepository.existsByIpAndBlockedUntilAfter(ip, LocalDateTime.now());
    }

    /**
     * 관리자 수동 차단. 화이트리스트 IP는 필터에서 면제되므로 차단이 무의미하여 거부한다.
     *
     * @return 차단됐으면 true, 화이트리스트/이미 차단 중이면 false
     */
    @Transactional
    public boolean blockManually(String ip, int minutes, String reason, String actor) {
        if (whitelist.isWhitelisted(ip) || isBlocked(ip)) {
            return false;
        }
        block(ip, reason, actor, minutes, false);
        return true;
    }

    /**
     * 관리자 수동 해제. 유효한 차단 레코드를 제거하고 IP_UNBLOCKED 를 기록한다
     * (이 기록 시점부터 실패 집계가 다시 시작된다).
     *
     * @return 실제로 해제된 차단이 있었으면 true
     */
    @Transactional
    public boolean unblock(String ip, String actor) {
        long removed = blockedIpRepository.deleteByIpAndBlockedUntilAfter(ip, LocalDateTime.now());
        if (removed == 0) {
            return false;
        }
        events.record(AuditEventType.IP_UNBLOCKED, AuditResult.SUCCESS, actor, ip, "관리자 수동 해제");
        return true;
    }

    private void block(String ip, String reason, String actor, int minutes, boolean notify) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plusMinutes(minutes);
        blockedIpRepository.save(new BlockedIp(ip, reason, now, until));
        events.record(AuditEventType.IP_BLOCKED, AuditResult.BLOCKED, actor, ip, reason + " (해제 예정 " + until + ")");
        metrics.ipBlocked();
        if (notify) {
            alertService.notify("IP_BLOCKED", "ip=" + ip + ", " + reason + ", 해제 예정 " + until);
        }
    }

    /** 사용자명 로깅 시 과도한 길이/개행 차단. 비밀번호 등 민감정보는 애초에 전달하지 않는다. */
    private String safe(String username) {
        if (username == null) {
            return "(none)";
        }
        String trimmed = username.replaceAll("[\\r\\n]", " ");
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }
}
