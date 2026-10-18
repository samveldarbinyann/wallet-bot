package com.example.walletbot.platform.blockchain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rpc")
public record RpcProperties(
        Endpoint eth,
        Endpoint bnb,
        Endpoint btc,
        Endpoint sol,
        Endpoint ton,
        Endpoint tron
) {
    public record Endpoint(String url, String key) {
    }
}