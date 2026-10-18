package com.example.walletbot.user.api.dto;

public record TwoFaLoginResponse(
        String token,
        UserResponse user
) {
}
