package com.example.walletbot.deposit.application;

import com.example.walletbot.deposit.infrastructure.AlchemyWebhookProperties;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.security.HashingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlchemyWebhookSignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final AlchemyWebhookProperties properties;
    private final HashingService hashingService;

    public boolean isValid(BlockchainNetwork network, byte[] body, String signatureHeader) {
        if (body == null || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        AlchemyWebhookProperties.Endpoint endpoint = properties.forNetwork(network);
        if (endpoint == null || endpoint.signingKey() == null || endpoint.signingKey().isBlank()) {
            log.warn("Alchemy webhook rejected: no signing key configured for {}", network);
            return false;
        }
        String expected = hmacHex(endpoint.signingKey(), body);
        return expected != null && hashingService.safeEquals(expected, signatureHeader.trim());
    }

    private String hmacHex(String key, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            log.warn("Alchemy signature computation failed: {}", e.getMessage());
            return null;
        }
    }
}