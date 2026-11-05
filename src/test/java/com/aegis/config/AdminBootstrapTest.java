package com.aegis.config;

import com.aegis.auth.Role;
import com.aegis.auth.User;
import com.aegis.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/** 관리자 부트스트랩: 환경변수 값이 있을 때만, 해시로만, 기존 계정은 건드리지 않고 생성한다. */
@SpringBootTest
class AdminBootstrapTest {

    private static final String PW = "Adm1n!Passw0rd";

    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    private void runWith(String username, String password) {
        new AdminBootstrap(users, encoder, username, password).run();
    }

    @Test
    void 값이_없으면_계정을_만들지_않는다() {
        runWith("", "");
        assertThat(users.count()).isZero();
    }

    @Test
    void 아이디와_비밀번호가_있으면_ADMIN을_해시로_생성한다() {
        runWith("owner_admin", PW);
        User u = users.findByUsername("owner_admin").orElseThrow();
        assertThat(u.getRole()).isEqualTo(Role.ADMIN);
        assertThat(u.getPasswordHash()).isNotEqualTo(PW).startsWith("$2");
        assertThat(encoder.matches(PW, u.getPasswordHash())).isTrue();
    }

    @Test
    void 형식이_잘못되면_만들지_않는다() {
        runWith("a<b>", PW);       // 위험한 아이디
        runWith("owner_admin", "short"); // 짧은 비밀번호
        runWith("owner_admin", "");      // 비밀번호 없음
        assertThat(users.count()).isZero();
    }

    @Test
    void 이미_있는_계정은_바꾸지_않는다() {
        users.save(new User("owner_admin", encoder.encode("Original!Pass1"), Role.USER));
        runWith("owner_admin", PW);
        User u = users.findByUsername("owner_admin").orElseThrow();
        assertThat(u.getRole()).isEqualTo(Role.USER);
        assertThat(encoder.matches("Original!Pass1", u.getPasswordHash())).isTrue();
    }
}
