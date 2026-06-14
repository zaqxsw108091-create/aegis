package com.aegis.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * 보안 감사 로그. 보안 이벤트(로그인/실패/차단/권한거부)를 기록한다.
 * 기록 항목: 시각(createdAt), 행위자(actor), IP, 이벤트유형(type), 결과(result).
 * 민감정보(비밀번호/토큰)는 actor/detail 에 담지 않는다.
 *
 * <p>append-only 정책: {@link Immutable} 로 INSERT 후 UPDATE를 금지한다(Hibernate가 갱신 SQL을
 * 발행하지 않음). 엔티티에 setter가 없어 내용 변경 경로 자체가 없다. 애플리케이션 코드는 감사 로그를
 * 수정/삭제하지 않는다(보관 주기 관리 등 운영 작업은 별도 절차로만).
 */
@Entity
@Immutable
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

    /** 행위자(시도한 사용자명 등). 익명/미상이면 null. */
    @Column(length = 100)
    private String actor;

    @Column(length = 64)
    private String ip;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(AuditEventType type, AuditResult result, String actor, String ip, String detail,
                    LocalDateTime createdAt) {
        this.type = type;
        this.result = result;
        this.actor = actor;
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

    public String getActor() {
        return actor;
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
