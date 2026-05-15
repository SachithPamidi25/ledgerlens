package com.ledgerlens.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken
) {}