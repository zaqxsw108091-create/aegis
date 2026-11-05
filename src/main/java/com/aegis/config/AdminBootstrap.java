package com.aegis.config;

import com.aegis.auth.Role;
import com.aegis.auth.User;
import com.aegis.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 최초 관리자(ROLE_ADMIN) 계정 부트스트랩.
 *
 * <p>환경변수 {@code AEGIS_ADMIN_USERNAME} / {@code AEGIS_ADMIN_PASSWORD} 가 <b>둘 다</b> 주어졌을 때만
 * 해당 관리자 계정을 만든다(이미 존재하면 아무것도 바꾸지 않는다). 소스에는 어떤 자격증명도 없으며,
 * 비밀번호는 BCrypt 해시로만 저장하고 로그에는 절대 남기지 않는다. 값이 없으면 아무 계정도 만들지 않는다.
 * 검증은 회원가입과 같은 규칙(아이디: 영문/숫자/밑줄 3~50자, 비밀번호: 8~72자)을 따른다.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,50}$");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    @Autowired
    public AdminBootstrap(UserRepository users, PasswordEncoder encoder,
                          @Value("${AEGIS_ADMIN_USERNAME:}") String username,
                          @Value("${AEGIS_ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    public void run(String... args) {
        if (username.isEmpty() && password.isEmpty()) {
            log.info("[ADMIN-BOOTSTRAP] AEGIS_ADMIN_USERNAME/AEGIS_ADMIN_PASSWORD 미설정 - 관리자 계정을 만들지 않습니다.");
            return;
        }
        if (!USERNAME.matcher(username).matches() || password.length() < 8 || password.length() > 72) {
            // 값 자체는 로그에 남기지 않는다.
            log.warn("[ADMIN-BOOTSTRAP] 관리자 계정 미생성: 아이디(영문/숫자/밑줄 3~50자) 또는 비밀번호(8~72자) 형식이 올바르지 않습니다.");
            return;
        }
        if (users.existsByUsername(username)) {
            log.info("[ADMIN-BOOTSTRAP] 이미 존재하는 계정이라 변경하지 않습니다: username={}", username);
            return;
        }
        users.save(new User(username, encoder.encode(password), Role.ADMIN));
        log.warn("[ADMIN-BOOTSTRAP] 관리자 계정 생성: username={} role={}", username, Role.ADMIN);
    }
}
