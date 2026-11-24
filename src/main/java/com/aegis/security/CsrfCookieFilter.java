package com.aegis.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * CSRF 토큰을 "응답을 쓰기 전에" 미리 확정시켜 XSRF-TOKEN 쿠키가 반드시 내려가게 한다.
 *
 * <p><b>왜 필요한가</b> — Spring Security 6은 CSRF 토큰을 지연(deferred) 로딩한다. 즉 누군가
 * {@link CsrfToken#getToken()} 을 호출해야 그때 토큰이 만들어지고 쿠키가 저장된다. 서버 렌더링
 * 화면에서는 Thymeleaf 가 폼을 그릴 때 비로소 토큰을 읽는데, 그 시점엔 응답 전송이 이미 시작돼
 * {@code Set-Cookie} 헤더가 실리지 못한다. 그 결과 브라우저에 쿠키가 없어 매 요청 새 토큰이
 * 생성되고, 폼으로 보낸 토큰과 서버가 기대하는 토큰이 영원히 불일치해 상태 변경 요청이 403 이 된다.
 *
 * <p>이 필터는 체인 진행 전에 토큰을 한 번 읽어 쿠키 저장을 앞당긴다. 기본 핸들러
 * (XorCsrfTokenRequestAttributeHandler)를 그대로 두므로 BREACH 보호는 유지된다.
 *
 * <p>CSRF 예외 경로(/api/**)에서는 요청 속성이 없으므로 아무 일도 하지 않는다.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // 토큰 값을 읽는 행위 자체가 지연 로딩을 실행시켜 쿠키를 저장하게 한다.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
