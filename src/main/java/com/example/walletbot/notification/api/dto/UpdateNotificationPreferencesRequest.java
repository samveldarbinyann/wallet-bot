package com.example.walletbot.notification.api.dto;

public record UpdateNotificationPreferencesRequest(
        Boolean pushEnabled,
        Boolean emailEnabled
) {
}