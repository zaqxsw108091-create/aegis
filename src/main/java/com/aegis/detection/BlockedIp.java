package com.aegis.detection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 자동 차단된 IP 기록. blockedUntil 이 지나면 자동으로 차단 해제(만료)된다.
 */
@Entity
@Table(name = "blocked_ip")
public class BlockedIp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String ip;

    @Column(length = 255)
    private String reason;

    @Column(name = "blocked_at", nullable = false)
    private LocalDateTime blockedAt;

    @Column(name = "blocked_until", nullable = false)
    private LocalDateTime blockedUntil;

    protected BlockedIp() {
        // JPA
    }

    public BlockedIp(String ip, String reason, LocalDateTime blockedAt, LocalDateTime blockedUntil) {
        this.ip = ip;
        this.reason = reason;
        this.blockedAt = blockedAt;
        this.blockedUntil = blockedUntil;
    }

    public Long getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getBlockedAt() {
        return blockedAt;
    }

    public LocalDateTime getBlockedUntil() {
        return blockedUntil;
    }
}
