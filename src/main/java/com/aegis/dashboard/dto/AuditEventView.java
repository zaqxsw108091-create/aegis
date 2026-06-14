package com.aegis.dashboard.dto;

import java.time.LocalDateTime;

/** 대시보드용 감사 이벤트 표시 DTO. */
public record AuditEventView(
        LocalDateTime timestamp,
        String type,
        String result,
        String actor,
        String ip,
        String detail
) {}
