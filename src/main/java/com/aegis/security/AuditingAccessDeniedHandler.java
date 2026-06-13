package com.aegis.security;

import com.aegis.audit.AuditEventType;
import com.aegis.audit.AuditResult;
import com.aegis.audit.AuditService;
import com.aegis.common.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * 권한 거부(인가 실패, CSRF 토큰 누락 등)를 감사 로그에 기록하고 403 JSON으로 응답한다.
 */
public class AuditingAccessDeniedHandler implements AccessDeniedHandler {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AuditingAccessDeniedHandler(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        String ip = request.getRemoteAddr();
        auditService.record(AuditEventType.ACCESS_DENIED, AuditResult.DENIED, ip,
                request.getMethod() + " " + request.getRequestURI());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = ErrorResponse.of(
                HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED", "접근 권한이 없습니다.");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
