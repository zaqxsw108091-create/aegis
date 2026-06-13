package com.aegis.auth;

import com.aegis.audit.AuditEventType;
import com.aegis.audit.AuditResult;
import com.aegis.audit.AuditService;
import com.aegis.auth.dto.SignupRequest;
import com.aegis.auth.dto.SignupResponse;
import com.aegis.auth.dto.TokenResponse;
import com.aegis.auth.exception.InvalidTokenException;
import com.aegis.auth.exception.UsernameAlreadyExistsException;
import com.aegis.detection.BruteForceProtectionService;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 회원가입 / 로그인 / 토큰 갱신. 계정 잠금(실패 카운트)과 토큰 발급을 담당한다.
 * 로그인 실패는 IP 단위 무차별 대입 탐지(BruteForceProtectionService)로도 함께 전달된다.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final BruteForceProtectionService bruteForce;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       LoginAttemptService loginAttemptService,
                       BruteForceProtectionService bruteForce,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.bruteForce = bruteForce;
        this.auditService = auditService;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException("이미 사용 중인 사용자명입니다.");
        }
        User user = new User(request.username(), passwordEncoder.encode(request.password()), Role.USER);
        User saved = userRepository.save(user);
        return new SignupResponse(saved.getId(), saved.getUsername(), saved.getRole().name());
    }

    public TokenResponse login(String username, String rawPassword, String ip) {
        User user = userRepository.findByUsername(username).orElse(null);

        // 사용자 미존재: 존재 여부를 노출하지 않고 동일하게 실패 처리(+IP 집계)
        if (user == null) {
            bruteForce.onLoginFailure(ip, username);
            throw new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.isLocked(now)) {
            throw new LockedException("계정이 잠겨 있습니다.");
        }
        // 잠금 만료 시 카운트 초기화
        loginAttemptService.clearExpiredLock(user.getId());

        if (!user.isEnabled()) {
            throw new DisabledException("비활성화된 계정입니다.");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            loginAttemptService.onFailure(user.getId());   // 계정 단위 실패/잠금
            bruteForce.onLoginFailure(ip, username);        // IP 단위 탐지 + 감사
            throw new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        loginAttemptService.onSuccess(user.getId());
        auditService.record(AuditEventType.LOGIN_SUCCESS, AuditResult.SUCCESS, ip, "username=" + username);
        return issueTokens(user);
    }

    public TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (Exception e) {
            throw new InvalidTokenException("유효하지 않은 토큰입니다.");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new InvalidTokenException("리프레시 토큰이 아닙니다.");
        }
        User user = userRepository.findByUsername(claims.getSubject())
                .orElseThrow(() -> new InvalidTokenException("토큰을 갱신할 수 없습니다."));
        if (!user.isEnabled() || user.isLocked(LocalDateTime.now())) {
            throw new InvalidTokenException("토큰을 갱신할 수 없습니다.");
        }
        return issueTokens(user);
    }

    private TokenResponse issueTokens(User user) {
        String access = jwtService.generateAccessToken(user.getUsername(), user.getRole());
        String refresh = jwtService.generateRefreshToken(user.getUsername());
        return TokenResponse.bearer(access, refresh, jwtService.getAccessExpirationMinutes());
    }
}
