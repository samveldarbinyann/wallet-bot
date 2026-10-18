package com.example.walletbot.user.domain;

import com.example.walletbot.shared.constant.UserRole;
import com.example.walletbot.shared.constant.UserStatus;

import java.time.Instant;

public record UserDto(
        Long id,
        Long telegramId,
        String username,
        String firstName,
        String lastName,
        String lang,
        String currency,
        String timezone,
        String theme,
        Instant createdAt,
        String maskedEmail,
        boolean emailVerified,
        boolean pinEnabled,
        boolean twoFaLoginEnabled,
        boolean notifyPushEnabled,
        boolean notifyEmailEnabled,
        UserRole role,
        UserStatus status,
        String statusReason
) {
}
