package com.aegis.auth.dto;

/**
 * 로그인/갱신 응답. Access + Refresh 토큰.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresInMinutes
) {
    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresInMinutes) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInMinutes);
    }
}
