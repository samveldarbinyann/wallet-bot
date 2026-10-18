package com.example.walletbot.deposit.application;

import com.example.walletbot.deposit.infrastructure.HeliusWebhookProperties;
import com.example.walletbot.shared.security.HashingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeliusWebhookSignatureVerifier {

    private final HeliusWebhookProperties properties;
    private final HashingService hashingService;

    public boolean isValid(String authHeader) {
        String expected = properties.authHeader();
        if (expected == null || expected.isBlank()) {
            log.warn("Helius webhook rejected: no auth header configured");
            return false;
        }
        return authHeader != null && hashingService.safeEquals(expected, authHeader.trim());
    }
}
