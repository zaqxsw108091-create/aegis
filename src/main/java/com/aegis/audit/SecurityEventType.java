package com.aegis.audit;

/**
 * 보안 감사 이벤트 종류.
 */
public enum SecurityEventType {
    /** 로그인(인증) 실패 */
    LOGIN_FAILURE,
    /** 무차별 대입 탐지로 IP 차단 */
    IP_BLOCKED,
    /** 레이트 리미트 초과로 요청 거부 */
    RATE_LIMITED
}
