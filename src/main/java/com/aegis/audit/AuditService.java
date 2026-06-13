package com.aegis.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
    public AuditLog record(AuditEventType type, AuditResult result, String ip, String detail) {
        AuditLog entry = repository.save(new AuditLog(type, result, ip, detail, LocalDateTime.now()));
        if (result == AuditResult.SUCCESS) {
            log.info("[AUDIT] {} result={} ip={} detail={}", type, result, ip, detail);
        } else {
            log.warn("[AUDIT] {} result={} ip={} detail={}", type, result, ip, detail);
        }
        return entry;
    }

    @Transactional(readOnly = true)
    public long countSince(AuditEventType type, String ip, LocalDateTime after) {
        return repository.countByTypeAndIpAndCreatedAtAfter(type, ip, after);
    }
}
