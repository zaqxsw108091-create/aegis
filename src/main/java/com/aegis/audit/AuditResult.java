package com.aegis.audit;

/**
 * 감사 이벤트의 결과.
 */
public enum AuditResult {
    /** 성공 */
    SUCCESS,
    /** 실패(잘못된 자격증명 등) */
    FAILURE,
    /** 차단(IP 차단/레이트 리미트로 거부) */
    BLOCKED,
    /** 권한 거부 */
    DENIED
}
