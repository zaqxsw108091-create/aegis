package com.aegis.auth;

import com.aegis.auth.dto.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 보호 자원 예시. JWT Access 토큰(Authorization: Bearer ...)으로 접근한다.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "account", description = "인증/권한이 필요한 보호 자원")
public class AccountController {

    @GetMapping("/me")
    @Operation(summary = "내 정보", description = "인증된 사용자 정보를 반환한다(ROLE_USER 이상).",
            security = @SecurityRequirement(name = "bearer"))
    public MeResponse me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_USER");
        return new MeResponse(authentication.getName(), role);
    }

    @GetMapping("/admin/ping")
    @Operation(summary = "관리자 전용", description = "ROLE_ADMIN 만 접근 가능. 그 외에는 403.",
            security = @SecurityRequirement(name = "bearer"))
    public Map<String, String> adminPing() {
        return Map.of("pong", "admin");
    }
}
