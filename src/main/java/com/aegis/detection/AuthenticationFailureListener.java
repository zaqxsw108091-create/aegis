package com.aegis.detection;

import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Spring Security의 인증 실패 이벤트를 받아 무차별 대입 탐지기에 전달한다.
 * (HTTP Basic/폼 로그인 등 어떤 인증 방식의 실패든 여기로 모인다 → P2 로그인도 자동 연동)
 */
@Component
public class AuthenticationFailureListener implements ApplicationListener<AbstractAuthenticationFailureEvent> {

    private final BruteForceProtectionService protection;

    public AuthenticationFailureListener(BruteForceProtectionService protection) {
        this.protection = protection;
    }

    @Override
    public void onApplicationEvent(AbstractAuthenticationFailureEvent event) {
        Authentication auth = event.getAuthentication();
        String ip = extractIp(auth);
        String username = (auth != null) ? String.valueOf(auth.getName()) : null;
        protection.onLoginFailure(ip, username);
    }

    private String extractIp(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof WebAuthenticationDetails details) {
            return details.getRemoteAddress();
        }
        return null;
    }
}
