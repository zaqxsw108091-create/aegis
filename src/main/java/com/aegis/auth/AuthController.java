package com.aegis.auth;

import com.aegis.auth.dto.LoginRequest;
import com.aegis.auth.dto.RefreshRequest;
import com.aegis.auth.dto.SignupRequest;
import com.aegis.auth.dto.SignupResponse;
import com.aegis.auth.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "auth", description = "회원가입 / 로그인 / 토큰 갱신")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "ROLE_USER 사용자를 생성한다. 비밀번호는 해싱되어 저장된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성됨"),
            @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 사용자명")
    })
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse body = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "성공 시 Access/Refresh 토큰을 발급한다. "
            + "비밀번호 5회 실패 시 계정이 15분 잠긴다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 발급"),
            @ApiResponse(responseCode = "401", description = "자격증명 불일치"),
            @ApiResponse(responseCode = "423", description = "계정 잠김")
    })
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request.username(), request.password(), http.getRemoteAddr());
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신(회전)", description = "유효한 Refresh 토큰으로 새 Access/Refresh 토큰을 발급한다. "
            + "Refresh 토큰은 1회용(회전)이며, 이미 사용된 토큰을 재사용하면 탈취로 간주해 전체 세션을 폐기한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 재발급"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은/재사용된 리프레시 토큰")
    })
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return authService.refresh(request.refreshToken(), http.getRemoteAddr());
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "Refresh 토큰을 폐기한다(멱등). 이후 해당 토큰으로 갱신 불가.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "폐기 완료")
    })
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        authService.logout(request.refreshToken(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
