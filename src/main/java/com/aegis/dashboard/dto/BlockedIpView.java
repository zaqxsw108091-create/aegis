package com.aegis.dashboard.dto;

import java.time.LocalDateTime;

public record BlockedIpView(
        String ip,
        String reason,
        LocalDateTime blockedAt,
        LocalDateTime blockedUntil
) {}
