package com.example.walletbot.user.api.dto;

import java.util.Map;

public record I18nResponse(
        String lang,
        Map<String, String> messages
) {
}
