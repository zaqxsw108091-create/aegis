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

    /**
     * 특정 IP의 가장 최근 해당 유형 이벤트(예: 마지막 수동 해제).
     *
     * <p>정렬 기준이 시각이 아니라 <b>id</b> 인 이유: 같은 순간(밀리초)에 여러 이벤트가 기록되면
     * 시각만으로는 순서를 가릴 수 없다. id 는 단조 증가라 항상 정확한 "가장 마지막"을 준다.
     */
    Optional<AuditLog> findFirstByTypeAndIpOrderByIdDesc(AuditEventType type, String ip);

    /**
     * 특정 IP의 해당 유형 이벤트 중 <b>기준 id 이후</b>이면서 시간창 안에 있는 건수.
     *
     * <p>"관리자가 해제한 뒤로 몇 번 실패했나"를 셀 때 쓴다. 시각 비교(초과)만 쓰면 해제 이벤트와
     * 같은 순간에 기록된 실패가 집계에서 빠져 재차단이 안 되는 문제가 있어 id 로 경계를 잡는다.
     */
    long countByTypeAndIpAndIdGreaterThanAndCreatedAtAfter(
            AuditEventType type, String ip, Long id, LocalDateTime after);

    /** 특정 행위자(사용자/시스템)가 관련된 모든 이벤트(최신순). 대시보드 "행위자 상세" 드릴다운용. */
    List<AuditLog> findByActorOrderByCreatedAtDesc(String actor, Pageable pageable);

    /** 특정 IP가 관련된 모든 이벤트(최신순). 대시보드 "IP 상세" 드릴다운용. */
    List<AuditLog> findByIpOrderByCreatedAtDesc(String ip, Pageable pageable);
}
