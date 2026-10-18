package com.example.walletbot.deposit.application;

import com.example.walletbot.deposit.infrastructure.HeliusWebhookProperties;
import com.example.walletbot.shared.security.HashingService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeliusWebhookSignatureVerifierTest {

    private static final String SECRET = "helius-auth-secret";
    private final HashingService hashingService = new HashingService("test-pin-pepper-0123456789");

    private HeliusWebhookSignatureVerifier verifier(String authHeader) {
        HeliusWebhookProperties props =
                new HeliusWebhookProperties("https://api.helius.xyz", "webhook-id", authHeader);
        return new HeliusWebhookSignatureVerifier(props, hashingService);
    }

    @Test
    void isValid_matchingSecret_true() {
        assertThat(verifier(SECRET).isValid(SECRET)).isTrue();
    }

    @Test
    void isValid_matchingSecretWithSurroundingWhitespace_true() {
        assertThat(verifier(SECRET).isValid("  " + SECRET + "  ")).isTrue();
    }

    @Test
    void isValid_wrongSecret_false() {
        assertThat(verifier(SECRET).isValid("nope")).isFalse();
    }

    @Test
    void isValid_missingHeader_false() {
        assertThat(verifier(SECRET).isValid(null)).isFalse();
    }

    @Test
    void isValid_noSecretConfigured_false() {
        assertThat(verifier("").isValid(SECRET)).isFalse();
    }
}
