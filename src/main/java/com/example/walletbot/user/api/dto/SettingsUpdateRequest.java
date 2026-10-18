package com.example.walletbot.user.api.dto;

public record SettingsUpdateRequest(
        String lang,
        String currency,
        String timezone,
        String theme
) {
}
