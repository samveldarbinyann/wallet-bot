package com.example.walletbot.platform.blockchain.util;

public final class RpcEndpointResolver {

    private RpcEndpointResolver() {
    }

    public static String buildUrl(String baseUrl, String apiKey) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String base = baseUrl.trim();
        if (apiKey == null || apiKey.isBlank()) {
            return base;
        }
        return base + apiKey.trim();
    }
}
