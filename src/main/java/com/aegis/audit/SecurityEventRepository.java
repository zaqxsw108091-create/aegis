package com.aegis.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    long countByTypeAndIpAndCreatedAtAfter(SecurityEventType type, String ip, LocalDateTime after);
}
