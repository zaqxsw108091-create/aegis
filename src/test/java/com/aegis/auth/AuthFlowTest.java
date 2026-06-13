package com.aegis.auth;

import com.aegis.audit.AuditLogRepository;
import com.aegis.auth.dto.LoginRequest;
import com.aegis.auth.dto.RefreshRequest;
import com.aegis.auth.dto.SignupRequest;
import com.aegis.auth.dto.TokenResponse;
import com.aegis.detection.BlockedIpRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 모듈 통합 테스트: 회원가입 / 로그인 성공·실패 / 계정 잠금 / 토큰 갱신 / 권한 거부.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private BlockedIpRepository blockedIpRepository;

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
        auditLogRepository.deleteAll();
        blockedIpRepository.deleteAll();
    }

    // 테스트 메서드별 고유 IP (IP 차단/레이트리밋 상호간섭 방지)
    private RequestPostProcessor ip(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private void signup(String username, String password, String ip) throws Exception {
        mvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new SignupRequest(username, password)))
                        .with(ip(ip)))
                .andExpect(status().isCreated());
    }

    private TokenResponse login(String username, String password, String ip) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new LoginRequest(username, password)))
                        .with(ip(ip)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readValue(body, TokenResponse.class);
    }

    @Test
    void 회원가입_성공_및_중복_409() throws Exception {
        String ip = "10.10.0.1";
        mvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new SignupRequest("alice", "password123")))
                        .with(ip(ip)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"));

        // 같은 사용자명 재가입 → 409
        mvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new SignupRequest("alice", "password123")))
                        .with(ip(ip)))
                .andExpect(status().isConflict());
    }

    @Test
    void 회원가입_검증실패_400() throws Exception {
        // 너무 짧은 비밀번호 + 허용되지 않는 사용자명 문자
        mvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new SignupRequest("a b!", "short")))
                        .with(ip("10.10.0.2")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 로그인_성공시_토큰발급되고_me로_접근가능() throws Exception {
        String ip = "10.10.0.3";
        signup("bob", "password123", ip);

        TokenResponse tokens = login("bob", "password123", ip);
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.tokenType()).isEqualTo("Bearer");

        mvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .with(ip(ip)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    void 로그인_비밀번호_틀리면_401() throws Exception {
        String ip = "10.10.0.4";
        signup("carol", "password123", ip);

        mvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new LoginRequest("carol", "wrong-password")))
                        .with(ip(ip)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 로그인_5회_실패시_계정잠금_이후_올바른_비밀번호도_423() throws Exception {
        String ip = "10.10.0.5";
        signup("dave", "password123", ip);

        // 5회 실패 (각 401)
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(APPLICATION_JSON)
                            .content(om.writeValueAsString(new LoginRequest("dave", "wrong-" + i)))
                            .with(ip(ip)))
                    .andExpect(status().isUnauthorized());
        }

        // 잠금 등록 확인
        assertThat(userRepository.findByUsername("dave").orElseThrow().getLockedUntil())
                .as("5회 실패 후 lockedUntil 이 설정되어야 한다")
                .isNotNull();

        // 올바른 비밀번호여도 잠금 상태라 423
        mvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new LoginRequest("dave", "password123")))
                        .with(ip(ip)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void 토큰갱신_성공_및_잘못된_리프레시토큰_401() throws Exception {
        String ip = "10.10.0.6";
        signup("erin", "password123", ip);
        TokenResponse tokens = login("erin", "password123", ip);

        // 정상 갱신
        String body = mvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new RefreshRequest(tokens.refreshToken())))
                        .with(ip(ip)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        TokenResponse refreshed = om.readValue(body, TokenResponse.class);
        assertThat(refreshed.accessToken()).isNotBlank();

        // 새 access 토큰으로 보호 자원 접근 가능
        mvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + refreshed.accessToken())
                        .with(ip(ip)))
                .andExpect(status().isOk());

        // 잘못된 리프레시 토큰 → 401
        mvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new RefreshRequest("not-a-valid-token")))
                        .with(ip(ip)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void access_토큰을_refresh로_사용하면_거부된다() throws Exception {
        String ip = "10.10.0.7";
        signup("frank", "password123", ip);
        TokenResponse tokens = login("frank", "password123", ip);

        // access 토큰을 refresh 엔드포인트에 넣으면 타입 불일치로 401
        mvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new RefreshRequest(tokens.accessToken())))
                        .with(ip(ip)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void 권한거부_USER가_ADMIN자원_접근시_403() throws Exception {
        String ip = "10.10.0.8";
        signup("grace", "password123", ip);
        TokenResponse tokens = login("grace", "password123", ip);

        mvc.perform(get("/api/admin/ping")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .with(ip(ip)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 토큰없이_보호자원_접근시_401() throws Exception {
        mvc.perform(get("/api/me").with(ip("10.10.0.9")))
                .andExpect(status().isUnauthorized());
    }
}
