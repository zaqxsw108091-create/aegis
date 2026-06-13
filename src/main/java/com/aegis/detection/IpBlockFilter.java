package com.aegis.detection;

import com.aegis.common.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 차단된 IP의 모든 요청을 403으로 막는다. (인증/공개 여부와 무관하게 가장 먼저 차단)
 */
public class IpBlockFilter extends OncePerRequestFilter {

    private final BruteForceProtectionService protection;
    private final ObjectMapper objectMapper;

    public IpBlockFilter(BruteForceProtectionService protection, ObjectMapper objectMapper) {
        this.protection = protection;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        if (protection.isBlocked(ip)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ErrorResponse body = ErrorResponse.of(
                    HttpServletResponse.SC_FORBIDDEN,
                    "IP_BLOCKED",
                    "차단된 IP입니다. 잠시 후 다시 시도하세요.");
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
