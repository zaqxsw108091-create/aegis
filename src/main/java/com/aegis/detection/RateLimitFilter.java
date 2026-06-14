package com.aegis.detection;

import com.aegis.audit.AuditEventType;
import com.aegis.audit.AuditResult;
import com.aegis.audit.AuditService;
import com.aegis.common.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP당 요청 레이트 리미팅. 한도 초과 시 429로 거부한다.
 * 화이트리스트 IP는 면제. 거부 시 메트릭 기록 + (쿨다운 적용) 감사 기록.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** 거부 이벤트를 DB에 기록하는 IP별 최소 간격(자기-DoS 방지). */
    private static final long REJECTION_RECORD_COOLDOWN_MS = 60_000L;

    private final RateLimiter rateLimiter;
    private final IpWhitelist whitelist;
    private final AuditService events;
    private final DetectionMetrics metrics;
    private final ObjectMapper objectMapper;
    private final int permitsPerMinute;
    private final Map<String, Long> lastRejectionRecordedAt = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimiter rateLimiter,
                           IpWhitelist whitelist,
                           AuditService events,
                           DetectionMetrics metrics,
                           ObjectMapper objectMapper,
                           int permitsPerMinute) {
        this.rateLimiter = rateLimiter;
        this.whitelist = whitelist;
        this.events = events;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.permitsPerMinute = permitsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = request.getRemoteAddr();

        // 화이트리스트 IP는 레이트리밋 면제.
        if (whitelist.isWhitelisted(ip)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!rateLimiter.tryConsume(ip)) {
            metrics.rateLimited();
            log.warn("[RATE-LIMIT] 한도 초과 ip={} uri={}", ip, request.getRequestURI());
            if (shouldRecordRejection(ip)) {
                events.record(AuditEventType.RATE_LIMITED, AuditResult.BLOCKED, null, ip,
                        "분당 " + permitsPerMinute + "회 초과");
            }
            response.setStatus(429); // 429 Too Many Requests
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ErrorResponse body = ErrorResponse.of(429, "RATE_LIMITED", "요청이 너무 많습니다. 잠시 후 다시 시도하세요.");
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 폭주 시 동일 IP의 거부 로그가 DB를 채우지 않도록 IP당 쿨다운(1분)으로 1회만 기록. */
    private boolean shouldRecordRejection(String ip) {
        long now = System.currentTimeMillis();
        Long last = lastRejectionRecordedAt.get(ip);
        if (last == null || now - last > REJECTION_RECORD_COOLDOWN_MS) {
            lastRejectionRecordedAt.put(ip, now);
            return true;
        }
        return false;
    }
}
