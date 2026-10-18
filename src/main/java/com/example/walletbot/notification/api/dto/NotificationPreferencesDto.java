package com.example.walletbot.notification.api.dto;

public record NotificationPreferencesDto(
        boolean pushEnabled,
        boolean emailEnabled
) {
}