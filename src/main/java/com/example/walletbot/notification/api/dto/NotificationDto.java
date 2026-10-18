package com.example.walletbot.notification.api.dto;

public record NotificationDto(
        String id,
        String type,
        String title,
        String body,
        String data,
        boolean read,
        String createdAt
) {
}