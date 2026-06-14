package com.aegis.audit;

import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * 대화형 로그인 성공(InteractiveAuthenticationSuccessEvent)을 감사 로그에 기록한다.
 *
 * <p>주의: 무상태 HTTP Basic은 매 요청 재인증하지만 이 이벤트를 발행하지 않으므로
 * 로그 폭주가 없다. P2의 폼/토큰 로그인이 들어오면 로그인 시점에만 1회 기록된다.
 */
@Component
public class AuthenticationSuccessAuditListener
        implements ApplicationListener<InteractiveAuthenticationSuccessEvent> {

    private final AuditService auditService;

    public AuthenticationSuccessAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void onApplicationEvent(InteractiveAuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        String ip = (auth != null && auth.getDetails() instanceof WebAuthenticationDetails details)
                ? details.getRemoteAddress() : null;
        String username = (auth != null) ? String.valueOf(auth.getName()) : "(unknown)";
        auditService.record(AuditEventType.LOGIN_SUCCESS, AuditResult.SUCCESS, username, ip, "로그인 성공");
    }
}
