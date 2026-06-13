package com.aegis.auth.dto;

public record SignupResponse(
        Long id,
        String username,
        String role
) {}
