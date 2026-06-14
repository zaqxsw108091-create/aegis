package com.aegis.dashboard.dto;

/** Micrometer 카운터 현재값 요약(/actuator/prometheus 와 동일 소스). */
public record MetricsSummary(
        double loginFailures,
        double ipBlocked,
        double rateLimited
) {}
