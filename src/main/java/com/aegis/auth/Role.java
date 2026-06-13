package com.aegis.auth;

/**
 * 사용자 역할. 권한 문자열은 "ROLE_" + name() 으로 매핑된다(ROLE_USER, ROLE_ADMIN).
 */
public enum Role {
    USER,
    ADMIN
}
