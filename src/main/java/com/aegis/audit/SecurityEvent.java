package com.aegis.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 보안 이벤트 감사 로그 (DB 기록).
 * 민감정보(비밀번호/토큰)는 detail 에 담지 않는다.
 */
@Entity
@Table(name = "security_event")
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SecurityEventType type;

    @Column(length = 64)
    private String ip;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SecurityEvent() {
        // JPA
    }

    public SecurityEvent(SecurityEventType type, String ip, String detail, LocalDateTime createdAt) {
        this.type = type;
        this.ip = ip;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public SecurityEventType getType() {
        return type;
    }

    public String getIp() {
        return ip;
    }

    public String getDetail() {
        return detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
