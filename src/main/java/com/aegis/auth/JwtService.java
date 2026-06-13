package com.aegis.auth;

import com.aegis.config.AegisSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 발급/검증. Access/Refresh 토큰을 구분(type 클레임)한다.
 * 시크릿은 환경변수(AEGIS_JWT_SECRET)로 주입된 값을 사용한다.
 */
@Service
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessExpirationMinutes;
    private final long refreshExpirationMinutes;

    public JwtService(AegisSecurityProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMinutes = props.jwt().expirationMinutes();
        this.refreshExpirationMinutes = props.jwt().refreshExpirationMinutes();
    }

    public String generateAccessToken(String username, Role role) {
        return build(username, accessExpirationMinutes, TYPE_ACCESS, role.name());
    }

    public String generateRefreshToken(String username) {
        return build(username, refreshExpirationMinutes, TYPE_REFRESH, null);
    }

    private String build(String subject, long minutes, String type, String role) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(subject)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(minutes, ChronoUnit.MINUTES)));
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(key).compact();
    }

    /** 서명/만료 검증 후 Claims 반환. 유효하지 않으면 JwtException. */
    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        return jws.getPayload();
    }

    public long getAccessExpirationMinutes() {
        return accessExpirationMinutes;
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get("type", String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get("type", String.class));
    }
}
