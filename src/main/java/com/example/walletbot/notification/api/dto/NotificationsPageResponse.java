package com.example.walletbot.notification.api.dto;

import java.util.List;

public record NotificationsPageResponse(
        List<NotificationDto> items,
        int page,
        int totalPages,
        long unreadCount
) {
}