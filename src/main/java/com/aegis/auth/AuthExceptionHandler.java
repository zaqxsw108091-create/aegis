package com.aegis.auth;

import com.aegis.auth.exception.InvalidTokenException;
import com.aegis.auth.exception.UsernameAlreadyExistsException;
import com.aegis.common.error.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 인증/계정 관련 예외를 일관된 ErrorResponse 로 변환한다.
 * (전역 GlobalExceptionHandler 보다 우선해 구체 예외를 먼저 처리)
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(LockedException ex) {
        return build(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "로그인 실패가 누적되어 계정이 잠겼습니다. 잠시 후 다시 시도하세요.");
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex) {
        return build(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "비활성화된 계정입니다.");
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(UsernameAlreadyExistsException ex) {
        return build(HttpStatus.CONFLICT, "USERNAME_TAKEN", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "USERNAME_TAKEN", "이미 사용 중인 사용자명입니다.");
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException ex) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), code, message));
    }
}
