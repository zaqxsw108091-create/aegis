package com.aegis.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    long countByTypeAndIpAndCreatedAtAfter(AuditEventType type, String ip, LocalDateTime after);

    // 대시보드 조회용
    List<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByType(AuditEventType type);

    long countByTypeAndCreatedAtAfter(AuditEventType type, LocalDateTime after);

    /** 특정 IP의 가장 최근 해당 유형 이벤트(예: 마지막 수동 해제 시각). */
    Optional<AuditLog> findFirstByTypeAndIpOrderByCreatedAtDesc(AuditEventType type, String ip);

    /** 특정 행위자(사용자/시스템)가 관련된 모든 이벤트(최신순). 대시보드 "행위자 상세" 드릴다운용. */
    List<AuditLog> findByActorOrderByCreatedAtDesc(String actor, Pageable pageable);

    /** 특정 IP가 관련된 모든 이벤트(최신순). 대시보드 "IP 상세" 드릴다운용. */
    List<AuditLog> findByIpOrderByCreatedAtDesc(String ip, Pageable pageable);
}
