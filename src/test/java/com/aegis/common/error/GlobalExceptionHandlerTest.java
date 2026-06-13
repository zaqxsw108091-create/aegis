package com.aegis.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 예상치_못한_예외는_500과_일반메시지로_응답하고_내부정보를_노출하지_않는다() {
        // 내부 메시지에 민감정보(비밀번호 등)가 섞여 있어도 응답엔 새지 않아야 한다.
        Exception internal = new RuntimeException("DB 연결 실패: password=super-secret-123");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(internal);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(500);
        assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.message()).isEqualTo("서버 내부 오류가 발생했습니다.");
        assertThat(body.timestamp()).isNotNull();
        // 내부 예외 메시지/시크릿이 응답으로 새지 않는다.
        assertThat(body.message()).doesNotContain("password", "secret", "DB");
    }

    @Test
    void ErrorResponse_of_는_타임스탬프를_채운다() {
        ErrorResponse r = ErrorResponse.of(400, "VALIDATION_ERROR", "bad");
        assertThat(r.timestamp()).isNotNull();
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(r.message()).isEqualTo("bad");
    }
}
