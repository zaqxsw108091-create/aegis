package com.aegis.auth;

import com.aegis.config.AegisSecurityProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 핵심 보안 로직 단위 테스트: JWT 발급/검증과 토큰 타입 구분.
 */
class JwtServiceTest {

    private JwtService newService() {
        var jwt = new AegisSecurityProperties.Jwt(
                "0123456789012345678901234567890123456789", 30, 10080); // 40바이트 시크릿
        var props = new AegisSecurityProperties(
                jwt,
                new AegisSecurityProperties.Lockout(5, 15),
                new AegisSecurityProperties.Bruteforce(10, 10, 10),
                new AegisSecurityProperties.RateLimit(60),
                List.of());
        return new JwtService(props);
    }

    @Test
    void access_토큰은_subject와_role과_타입을_담는다() {
        JwtService jwt = newService();
        String token = jwt.generateAccessToken("alice", Role.ADMIN);

        Claims claims = jwt.parse(token);
        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(jwt.isAccessToken(claims)).isTrue();
        assertThat(jwt.isRefreshToken(claims)).isFalse();
    }

    @Test
    void refresh_토큰은_refresh_타입이다() {
        JwtService jwt = newService();
        Claims claims = jwt.parse(jwt.generateRefreshToken("bob"));
        assertThat(claims.getSubject()).isEqualTo("bob");
        assertThat(jwt.isRefreshToken(claims)).isTrue();
        assertThat(jwt.isAccessToken(claims)).isFalse();
    }

    @Test
    void 위조되거나_잘못된_토큰은_검증에서_예외() {
        JwtService jwt = newService();
        assertThatThrownBy(() -> jwt.parse("garbage.token.value"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void 다른_시크릿으로_서명된_토큰은_거부된다() {
        JwtService a = newService();
        // 다른 시크릿 서비스
        var jwt2 = new AegisSecurityProperties.Jwt(
                "ABCDEFGHIJABCDEFGHIJABCDEFGHIJABCDEFGHIJ", 30, 10080);
        var props2 = new AegisSecurityProperties(jwt2,
                new AegisSecurityProperties.Lockout(5, 15),
                new AegisSecurityProperties.Bruteforce(10, 10, 10),
                new AegisSecurityProperties.RateLimit(60),
                List.of());
        JwtService b = new JwtService(props2);

        String tokenFromB = b.generateAccessToken("eve", Role.USER);
        // a 의 시크릿으로는 b 토큰 검증 실패(서명 불일치)
        assertThatThrownBy(() -> a.parse(tokenFromB))
                .isInstanceOf(Exception.class);
    }
}
