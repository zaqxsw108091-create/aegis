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

/**
 * IP당 요청 레이트 리미팅. 한도 초과 시 429로 거부한다.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitService rateLimitService;
    private final AuditService events;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService,
                           AuditService events,
                           ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        if (!rateLimitService.tryConsume(ip)) {
            log.warn("[RATE-LIMIT] 한도 초과 ip={} uri={}", ip, request.getRequestURI());
            if (rateLimitService.shouldRecordRejection(ip)) {
                events.record(AuditEventType.RATE_LIMITED, AuditResult.BLOCKED, ip,
                        "분당 " + rateLimitService.getRequestsPerMinute() + "회 초과");
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
}
