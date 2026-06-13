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
 * 보안 감사 로그. 보안 이벤트(로그인/실패/차단/권한거부)를 기록한다.
 * 기록 항목: 시각(createdAt), IP, 이벤트유형(type), 결과(result).
 * 민감정보(비밀번호/토큰)는 detail 에 담지 않는다.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private AuditEventType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private AuditResult result;

    @Column(length = 64)
    private String ip;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(AuditEventType type, AuditResult result, String ip, String detail, LocalDateTime createdAt) {
        this.type = type;
        this.result = result;
        this.ip = ip;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public AuditEventType getType() {
        return type;
    }

    public AuditResult getResult() {
        return result;
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
