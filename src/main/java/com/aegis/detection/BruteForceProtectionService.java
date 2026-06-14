package com.aegis.detection;

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
    private final AegisSecurityProperties props;

    public BruteForceProtectionService(AuditService events,
                                       BlockedIpRepository blockedIpRepository,
                                       IpWhitelist whitelist,
                                       DetectionMetrics metrics,
                                       AegisSecurityProperties props) {
        this.events = events;
        this.blockedIpRepository = blockedIpRepository;
        this.whitelist = whitelist;
        this.metrics = metrics;
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
        long recentFailures = events.countSince(AuditEventType.LOGIN_FAILURE, ip, windowStart);

        if (recentFailures >= bf.ipFailThreshold() && !isBlocked(ip)) {
            block(ip, "brute-force 탐지: " + bf.ipFailWindowMinutes() + "분 내 실패 " + recentFailures + "회");
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

    private void block(String ip, String reason) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plusMinutes(props.bruteforce().ipBlockMinutes());
        blockedIpRepository.save(new BlockedIp(ip, reason, now, until));
        events.record(AuditEventType.IP_BLOCKED, AuditResult.BLOCKED, "system", ip, reason + " (해제 예정 " + until + ")");
        metrics.ipBlocked();
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
