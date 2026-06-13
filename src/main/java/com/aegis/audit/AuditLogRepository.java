package com.aegis.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    long countByTypeAndIpAndCreatedAtAfter(AuditEventType type, String ip, LocalDateTime after);
}
