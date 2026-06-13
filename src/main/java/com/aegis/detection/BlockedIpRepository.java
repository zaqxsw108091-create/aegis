package com.aegis.detection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface BlockedIpRepository extends JpaRepository<BlockedIp, Long> {

    /** 해당 IP가 현재(now 기준) 유효하게 차단되어 있는지. */
    boolean existsByIpAndBlockedUntilAfter(String ip, LocalDateTime now);
}
