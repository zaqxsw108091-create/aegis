package com.aegis.audit;

/**
 * 감사 로그 이벤트 종류.
 */
public enum AuditEventType {
    /** 로그인(인증) 성공 */
    LOGIN_SUCCESS,
    /** 로그인(인증) 실패 */
    LOGIN_FAILURE,
    /** 무차별 대입 탐지로 IP 차단 */
    IP_BLOCKED,
    /** 레이트 리미트 초과로 요청 거부 */
    RATE_LIMITED,
    /** 권한 거부(인가 실패) */
    ACCESS_DENIED,
    /** 리프레시 토큰 재사용 감지(탈취 의심) → 전체 세션 폐기 */
    TOKEN_REUSE,
    /** 로그아웃(리프레시 토큰 폐기) */
    LOGOUT
}
