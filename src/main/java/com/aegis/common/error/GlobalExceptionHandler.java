package com.aegis.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * 전역 예외 처리. 모든 에러를 {@link ErrorResponse} 단일 포맷으로 통일한다.
 *
 * <p>{@link ResponseEntityExceptionHandler}를 상속해 스프링 MVC 프레임워크 예외
 * (404/405/415/검증 실패 등)는 올바른 상태코드를 유지하면서 본문만 공통 DTO로 바꾼다.
 * 그 외 예상치 못한 예외는 500으로 처리하되 <b>스택트레이스/내부 메시지는 응답에 노출하지 않는다</b>.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** @Valid 검증 실패: 어떤 필드가 왜 틀렸는지(메시지)는 안전하므로 담는다. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                detail.isBlank() ? "요청 값이 유효하지 않습니다." : detail);
        return new ResponseEntity<>(body, headers, HttpStatus.BAD_REQUEST);
    }

    /** 나머지 모든 스프링 MVC 프레임워크 예외: 상태코드는 유지하고 본문만 공통 포맷으로. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        String code = (resolved != null) ? resolved.name() : "ERROR";
        String message = (resolved != null) ? resolved.getReasonPhrase() : "오류가 발생했습니다.";
        ErrorResponse error = ErrorResponse.of(statusCode.value(), code, message);
        return new ResponseEntity<>(error, headers, statusCode);
    }

    /** 그 외 예상치 못한 예외: 내부 정보는 로그에만, 응답은 일반 메시지로. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // 상세 원인(스택트레이스)은 서버 로그에만 남기고 클라이언트에는 노출하지 않는다.
        log.error("처리되지 않은 예외 발생", ex);
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "서버 내부 오류가 발생했습니다.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
