package com.example.walletbot.user.api.dto;

public record UserResponse(
        String id,
        String telegramId,
        String username,
        String firstName,
        String lastName,
        String lang,
        String currency,
        String timezone,
        String theme,
        String createdAt,
        String email,
        boolean emailVerified,
        boolean pinEnabled,
        boolean twoFaLoginEnabled,
        boolean notifyPushEnabled,
        boolean notifyEmailEnabled,
        String role,
        String status,
        String statusReason
) {
}
