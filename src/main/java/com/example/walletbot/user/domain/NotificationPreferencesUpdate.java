package com.example.walletbot.user.domain;

public record NotificationPreferencesUpdate(
        Boolean pushEnabled,
        Boolean emailEnabled
) {
}