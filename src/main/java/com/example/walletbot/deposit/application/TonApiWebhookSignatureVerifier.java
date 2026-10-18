package com.example.walletbot.deposit.application;

import com.example.walletbot.deposit.infrastructure.TonApiWebhookProperties;
import com.example.walletbot.shared.security.HashingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TonApiWebhookSignatureVerifier {

    private final TonApiWebhookProperties properties;
    private final HashingService hashingService;

    public boolean isValid(String secret) {
        String expected = properties.secret();
        if (expected == null || expected.isBlank()) {
            log.warn("TonAPI webhook rejected: no secret configured");
            return false;
        }
        return secret != null && hashingService.safeEquals(expected, secret);
    }
}
