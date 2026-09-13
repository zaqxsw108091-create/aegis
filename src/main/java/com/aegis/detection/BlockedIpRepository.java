package com.aegis.detection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BlockedIpRepository extends JpaRepository<BlockedIp, Long> {

    /** 해당 IP가 현재(now 기준) 유효하게 차단되어 있는지. */
    boolean existsByIpAndBlockedUntilAfter(String ip, LocalDateTime now);

    /** 현재 유효한(만료 전) 차단 목록. */
    List<BlockedIp> findByBlockedUntilAfterOrderByBlockedUntilDesc(LocalDateTime now);

    long countByBlockedUntilAfter(LocalDateTime now);

    /** 해당 IP의 유효한(만료 전) 차단 레코드를 삭제한다(수동 해제). 삭제 건수 반환. */
    long deleteByIpAndBlockedUntilAfter(String ip, LocalDateTime now);
}
