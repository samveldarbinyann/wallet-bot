package com.example.walletbot.admin.domain;

import com.example.walletbot.shared.constant.UserRole;
import com.example.walletbot.shared.constant.UserStatus;

import java.time.Instant;

public record AdminUserDto(
        Long id,
        Long telegramId,
        String username,
        String firstName,
        String lastName,
        String maskedEmail,
        UserRole role,
        UserStatus status,
        String statusReason,
        Instant createdAt,
        Instant lastLoginAt
) {
}
