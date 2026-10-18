package com.example.walletbot.deposit.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tonapi.webhook")
public record TonApiWebhookProperties(
        String managementUrl,
        String id,
        String secret,
        String token
) {
    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public boolean isConfigured() {
        return notBlank(id) && notBlank(token);
    }
}
