package com.example.walletbot.deposit.application;

import com.example.walletbot.deposit.infrastructure.AlchemyWebhookProperties;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.security.HashingService;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class AlchemyWebhookSignatureVerifierTest {

    private static final String ETH_KEY = "eth-signing-key";
    private final HashingService hashingService = new HashingService("test-pin-pepper-0123456789");

    private static String sign(String key, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AlchemyWebhookSignatureVerifier verifier(String ethSigningKey) {
        AlchemyWebhookProperties props = new AlchemyWebhookProperties(
                "https://dashboard.alchemy.com", "auth-token",
                new AlchemyWebhookProperties.Endpoint("eth-id", ethSigningKey),
                new AlchemyWebhookProperties.Endpoint("bnb-id", "bnb-key"));
        return new AlchemyWebhookSignatureVerifier(props, hashingService);
    }

    @Test
    void isValid_correctSignature_true() {
        byte[] body = "{\"event\":{}}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier(ETH_KEY).isValid(BlockchainNetwork.ETH, body, sign(ETH_KEY, body))).isTrue();
    }

    @Test
    void isValid_wrongSignature_false() {
        byte[] body = "{\"event\":{}}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier(ETH_KEY).isValid(BlockchainNetwork.ETH, body, "deadbeef")).isFalse();
    }

    @Test
    void isValid_signatureFromOtherKey_false() {
        byte[] body = "{\"event\":{}}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier(ETH_KEY).isValid(BlockchainNetwork.ETH, body, sign("other-key", body))).isFalse();
    }

    @Test
    void isValid_missingSignatureHeader_false() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier(ETH_KEY).isValid(BlockchainNetwork.ETH, body, null)).isFalse();
    }

    @Test
    void isValid_noSigningKeyConfigured_false() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier("").isValid(BlockchainNetwork.ETH, body, sign(ETH_KEY, body))).isFalse();
    }
}
