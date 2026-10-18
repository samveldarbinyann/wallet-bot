package com.example.walletbot.deposit.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("helius.webhook")
public record HeliusWebhookProperties(
        String managementUrl,
        String id,
        String authHeader
) {
    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public boolean isConfigured() {
        return notBlank(id);
    }
}
