package com.aegis.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 보안 이벤트를 콘솔과 DB에 동시에 기록한다.
 */
@Service
public class SecurityEventService {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventService.class);

    private final SecurityEventRepository repository;

    public SecurityEventService(SecurityEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SecurityEvent record(SecurityEventType type, String ip, String detail) {
        SecurityEvent event = repository.save(new SecurityEvent(type, ip, detail, LocalDateTime.now()));
        // 차단/레이트리밋은 경고, 그 외(로그인 실패 등)는 정보 수준으로 콘솔 기록
        if (type == SecurityEventType.IP_BLOCKED || type == SecurityEventType.RATE_LIMITED) {
            log.warn("[SECURITY] {} ip={} detail={}", type, ip, detail);
        } else {
            log.info("[SECURITY] {} ip={} detail={}", type, ip, detail);
        }
        return event;
    }

    @Transactional(readOnly = true)
    public long countSince(SecurityEventType type, String ip, LocalDateTime after) {
        return repository.countByTypeAndIpAndCreatedAtAfter(type, ip, after);
    }
}
