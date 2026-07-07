package com.aegis.auth;

import com.aegis.audit.AuditEventType;
import com.aegis.audit.AuditLogRepository;
import com.aegis.auth.dto.LoginRequest;
import com.aegis.auth.dto.RefreshRequest;
import com.aegis.auth.dto.SignupRequest;
import com.aegis.auth.dto.TokenResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh 토큰 회전(1회용)/재사용 감지(전체 폐기)/로그아웃 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RefreshRotationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void clean() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    private RequestPostProcessor ip(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private TokenResponse signupAndLogin(String username, String ip) throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new SignupRequest(username, "password123")))
                        .with(ip(ip)))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new LoginRequest(username, "password123")))
                        .with(ip(ip)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readValue(body, TokenResponse.class);
    }

    private int refreshStatus(String refreshToken, String ip) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new RefreshRequest(refreshToken)))
                        .with(ip(ip)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void 회전_재사용감지_전체폐기() throws Exception {
        String ip = "10.30.0.1";
        TokenResponse first = signupAndLogin("rot_user", ip);

        // 1) 정상 갱신(회전) → 200 + 새 토큰
        String body = mvc.perform(post("/api/auth/refresh").contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new RefreshRequest(first.refreshToken())))
                        .with(ip(ip)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        TokenResponse second = om.readValue(body, TokenResponse.class);
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());

        // 2) 이미 사용한(회전된) 첫 토큰 재사용 → 401 + TOKEN_REUSE 감사 기록
        assertThat(refreshStatus(first.refreshToken(), ip)).isEqualTo(401);
        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> l.getType() == AuditEventType.TOKEN_REUSE && "rot_user".equals(l.getActor()));

        // 3) 재사용 감지로 전체 세션 폐기 → 두 번째(정상 발급된) 토큰도 401
        assertThat(refreshStatus(second.refreshToken(), ip)).isEqualTo(401);
    }

    @Test
    void 로그아웃하면_해당_refresh는_폐기된다() throws Exception {
        String ip = "10.30.0.2";
        TokenResponse tokens = signupAndLogin("logout_user", ip);

        // 로그아웃 → 204
        mvc.perform(post("/api/auth/logout").contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(new RefreshRequest(tokens.refreshToken())))
                        .with(ip(ip)))
                .andExpect(status().isNoContent());

        // 폐기된 토큰으로 갱신 시도 → 401
        assertThat(refreshStatus(tokens.refreshToken(), ip)).isEqualTo(401);

        // 감사 로그에 LOGOUT 기록
        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> l.getType() == AuditEventType.LOGOUT && "logout_user".equals(l.getActor()));
    }
}
