package com.aegis.common.error;

import java.time.OffsetDateTime;

/**
 * 모든 에러 응답의 일관된 포맷.
 * 내부 스택트레이스/원인 메시지는 절대 담지 않는다.
 *
 * @param timestamp 발생 시각
 * @param status    HTTP 상태 코드 (숫자)
 * @param code      애플리케이션 에러 코드 (예: VALIDATION_ERROR, INTERNAL_ERROR)
 * @param message   사용자에게 보여줄 안전한 메시지
 */
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message
) {
    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(OffsetDateTime.now(), status, code, message);
    }
}
