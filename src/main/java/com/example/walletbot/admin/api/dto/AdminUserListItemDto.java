package com.example.walletbot.admin.api.dto;

public record AdminUserListItemDto(
        Long id,
        Long telegramId,
        String username,
        String firstName,
        String lastName,
        String email,
        String role,
        String status,
        String statusReason,
        String createdAt,
        String lastLoginAt
) {
}