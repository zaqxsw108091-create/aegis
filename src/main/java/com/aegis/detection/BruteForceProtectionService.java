package com.aegis.detection;

import com.aegis.audit.SecurityEventService;
import com.aegis.audit.SecurityEventType;
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

    private final SecurityEventService events;
    private final BlockedIpRepository blockedIpRepository;
    private final AegisSecurityProperties props;

    public BruteForceProtectionService(SecurityEventService events,
                                       BlockedIpRepository blockedIpRepository,
                                       AegisSecurityProperties props) {
        this.events = events;
        this.blockedIpRepository = blockedIpRepository;
        this.props = props;
    }

    /** 로그인(인증) 실패 1건을 기록하고, 임계치를 넘으면 IP를 차단한다. */
    @Transactional
    public void onLoginFailure(String ip, String username) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        events.record(SecurityEventType.LOGIN_FAILURE, ip, "username=" + safe(username));

        AegisSecurityProperties.Bruteforce bf = props.bruteforce();
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(bf.ipFailWindowMinutes());
        long recentFailures = events.countSince(SecurityEventType.LOGIN_FAILURE, ip, windowStart);

        if (recentFailures >= bf.ipFailThreshold() && !isBlocked(ip)) {
            block(ip, "brute-force 탐지: " + bf.ipFailWindowMinutes() + "분 내 실패 " + recentFailures + "회");
        }
    }

    /** 현재 차단 상태 여부. */
    @Transactional(readOnly = true)
    public boolean isBlocked(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return blockedIpRepository.existsByIpAndBlockedUntilAfter(ip, LocalDateTime.now());
    }

    private void block(String ip, String reason) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plusMinutes(props.bruteforce().ipBlockMinutes());
        blockedIpRepository.save(new BlockedIp(ip, reason, now, until));
        events.record(SecurityEventType.IP_BLOCKED, ip, reason + " (해제 예정 " + until + ")");
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
