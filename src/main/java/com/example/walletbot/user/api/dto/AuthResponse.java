package com.example.walletbot.user.api.dto;

public record AuthResponse(
        String token,
        UserResponse user
) {
}
