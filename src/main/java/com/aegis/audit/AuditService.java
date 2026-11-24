package com.aegis.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 보안 감사 로그를 콘솔과 DB(audit_log)에 동시에 기록한다.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditLog record(AuditEventType type, AuditResult result, String actor, String ip, String detail) {
        AuditLog entry = repository.save(new AuditLog(type, result, actor, ip, detail, LocalDateTime.now()));
        if (result == AuditResult.SUCCESS) {
            log.info("[AUDIT] {} result={} actor={} ip={} detail={}", type, result, actor, ip, detail);
        } else {
            log.warn("[AUDIT] {} result={} actor={} ip={} detail={}", type, result, actor, ip, detail);
        }
        return entry;
    }

    @Transactional(readOnly = true)
    public long countSince(AuditEventType type, String ip, LocalDateTime after) {
        return repository.countByTypeAndIpAndCreatedAtAfter(type, ip, after);
    }

    /** 특정 IP의 가장 최근 해당 유형 이벤트 id(없으면 empty). id 는 단조 증가라 순서가 정확하다. */
    @Transactional(readOnly = true)
    public Optional<Long> lastEventId(AuditEventType type, String ip) {
        return repository.findFirstByTypeAndIpOrderByIdDesc(type, ip).map(AuditLog::getId);
    }

    /** 기준 id 이후 + 시간창 안의 해당 유형 이벤트 건수(해제 이후 실패 재집계용). */
    @Transactional(readOnly = true)
    public long countSinceEventId(AuditEventType type, String ip, long afterId, LocalDateTime windowStart) {
        return repository.countByTypeAndIpAndIdGreaterThanAndCreatedAtAfter(type, ip, afterId, windowStart);
    }
}
