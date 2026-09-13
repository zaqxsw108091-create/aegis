package com.aegis.dashboard.dto;

import java.time.LocalDateTime;

/** 관리자용 사용자 현황(비밀번호 해시 등 민감정보는 포함하지 않는다). */
public record UserView(
        String username,
        String role,
        boolean enabled,
        int failedLoginCount,
        LocalDateTime lockedUntil,
        boolean locked
) {}
