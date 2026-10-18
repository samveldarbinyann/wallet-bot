package com.example.walletbot.notification.domain;

import com.example.walletbot.shared.constant.NotificationType;

import java.time.Instant;

public record NotificationDto(
        Long id,
        NotificationType type,
        String title,
        String body,
        String data,
        boolean read,
        Instant createdAt
) {
}
