package com.aegis.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    long countByTypeAndIpAndCreatedAtAfter(AuditEventType type, String ip, LocalDateTime after);

    // 대시보드 조회용
    List<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByType(AuditEventType type);

    long countByTypeAndCreatedAtAfter(AuditEventType type, LocalDateTime after);
}
