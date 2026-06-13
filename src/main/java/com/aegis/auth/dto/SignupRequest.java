package com.aegis.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. 화이트리스트 검증(영문/숫자/밑줄만 허용).
 */
public record SignupRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "영문/숫자/밑줄(_)만 사용할 수 있습니다.")
        String username,

        @NotBlank
        @Size(min = 8, max = 72, message = "비밀번호는 8~72자여야 합니다.")
        String password
) {}
