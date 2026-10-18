package com.example.walletbot.deposit.infrastructure;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("alchemy.webhook")
public record AlchemyWebhookProperties(
        String dashboardUrl,
        String authToken,
        Endpoint eth,
        Endpoint bnb
) {
    private static boolean hasId(Endpoint e) {
        return e != null && notBlank(e.id());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public Endpoint forNetwork(BlockchainNetwork network) {
        return switch (network) {
            case ETH -> eth;
            case BNB -> bnb;
            default -> null;
        };
    }

    public boolean isRegistrationConfigured() {
        return notBlank(authToken) && (hasId(eth) || hasId(bnb));
    }

    public record Endpoint(String id, String signingKey) {
    }
}