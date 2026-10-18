package com.example.walletbot.deposit.application;

import com.example.walletbot.deposit.infrastructure.TonApiWebhookProperties;
import com.example.walletbot.shared.security.HashingService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TonApiWebhookSignatureVerifierTest {

    private static final String SECRET = "tonapi-url-secret";
    private final HashingService hashingService = new HashingService("test-pin-pepper-0123456789");

    private TonApiWebhookSignatureVerifier verifier(String configuredSecret) {
        TonApiWebhookProperties props =
                new TonApiWebhookProperties("https://rt.tonapi.io", "5", configuredSecret, "token");
        return new TonApiWebhookSignatureVerifier(props, hashingService);
    }

    @Test
    void isValid_matchingSecret_true() {
        assertThat(verifier(SECRET).isValid(SECRET)).isTrue();
    }

    @Test
    void isValid_wrongSecret_false() {
        assertThat(verifier(SECRET).isValid("nope")).isFalse();
    }

    @Test
    void isValid_missingSecret_false() {
        assertThat(verifier(SECRET).isValid(null)).isFalse();
    }

    @Test
    void isValid_noSecretConfigured_false() {
        assertThat(verifier("").isValid(SECRET)).isFalse();
    }
}
