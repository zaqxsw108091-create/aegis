package com.aegis.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Refresh 토큰의 서버측 수명 관리(회전/폐기).
 *
 * <p>원칙:
 * <ul>
 *   <li>저장은 항상 SHA-256 해시로만(원문 저장 금지 — DB 유출 대비).</li>
 *   <li>1회용: {@link #consume}는 존재하면 삭제하고 true. 두 번째 사용은 false → 재사용 감지 신호.</li>
 *   <li>{@link #revokeAll}로 사용자의 모든 세션을 즉시 폐기할 수 있다(탈취 대응/로그아웃 전체).</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;

    public RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    /** 새 refresh 토큰을 등록한다(해시로). 만료 지난 레코드도 이때 정리한다. */
    @Transactional
    public void store(String username, String rawToken, LocalDateTime expiresAt) {
        repository.deleteByExpiresAtBefore(LocalDateTime.now()); // lazy 청소
        repository.save(new RefreshToken(hash(rawToken), username, expiresAt, LocalDateTime.now()));
    }

    /**
     * 토큰 1회 소비(회전). 저장돼 있으면 삭제 후 true.
     * 서명은 유효한데 저장소에 없으면 false = 이미 사용됐거나 폐기된 토큰(재사용 의심).
     */
    @Transactional
    public boolean consume(String rawToken) {
        return repository.findByTokenHash(hash(rawToken))
                .map(t -> {
                    repository.delete(t);
                    return true;
                })
                .orElse(false);
    }

    /** 사용자의 모든 refresh 토큰 폐기(재사용 감지 시/전체 로그아웃). */
    @Transactional
    public void revokeAll(String username) {
        repository.deleteByUsername(username);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원 JVM", e);
        }
    }
}
