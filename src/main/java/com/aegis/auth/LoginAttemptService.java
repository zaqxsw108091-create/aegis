package com.aegis.auth;

import com.aegis.config.AegisSecurityProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 계정 단위 로그인 실패/잠금 카운팅.
 *
 * <p>각 메서드는 독립 트랜잭션으로 커밋된다. 로그인 실패 시 AuthService가 예외를 던져도
 * (그 예외로 인한 롤백과 무관하게) 실패 카운트/잠금이 반드시 영속화되도록 별도 빈으로 분리했다.
 */
@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AegisSecurityProperties props;

    public LoginAttemptService(UserRepository userRepository, AegisSecurityProperties props) {
        this.userRepository = userRepository;
        this.props = props;
    }

    /** 실패 1회 반영. 임계치 도달 시 계정을 잠근다. */
    @Transactional
    public void onFailure(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            int count = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(count);
            if (count >= props.lockout().maxFailedAttempts()) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(props.lockout().lockMinutes()));
            }
            userRepository.save(user);
        });
    }

    /** 로그인 성공: 실패 카운트와 잠금 해제. */
    @Transactional
    public void onSuccess(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        });
    }

    /** 잠금이 만료됐으면 해제하고 카운트를 초기화한다. */
    @Transactional
    public void clearExpiredLock(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(LocalDateTime.now())) {
                user.setFailedLoginCount(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }
        });
    }
}
