package com.aegis.dashboard.dto;

import java.util.Map;

/** 대시보드 요약 통계. */
public record DashboardStats(
        long totalEvents,
        long loginFailuresTotal,
        long loginFailuresLast24h,
        long activeBlockedIps,
        Map<String, Long> eventCountsByType,
        MetricsSummary metrics
) {}
