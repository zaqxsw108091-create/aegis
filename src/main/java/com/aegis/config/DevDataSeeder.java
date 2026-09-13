package com.aegis.config;

import com.aegis.auth.Role;
import com.aegis.auth.User;
import com.aegis.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * dev 프로파일 전용 시드 계정 생성기 (수동 테스트 편의).
 *
 * <p><b>운영(prod)에서는 절대 실행되지 않는다</b>(@Profile("dev")). 비밀번호는 로그에 남기지 않으며
 * 자격증명은 docs/TEST_GUIDE.md 에만 문서화한다. 운영용 관리자 계정은 별도 절차로 생성한다.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public DevDataSeeder(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        // 관리자: daeyoung0 / 일반 사용자: admin (이름은 admin 이지만 권한은 USER)
        seed("daeyoung0", "dae0", Role.ADMIN);
        seed("admin", "12340", Role.USER);
    }

    private void seed(String username, String rawPassword, Role role) {
        if (users.existsByUsername(username)) {
            return;
        }
        users.save(new User(username, encoder.encode(rawPassword), role));
        // 비밀번호는 로그에 남기지 않는다(자격증명은 TEST_GUIDE.md 참고).
        log.warn("[DEV-SEED] 시드 계정 생성: username={} role={} (dev 전용, 운영 사용 금지)", username, role);
    }
}
