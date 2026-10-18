package com.example.walletbot.swap.provider.simpleswap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "swap.providers.simpleswap")
public record SimpleSwapProperties(
        String url,
        String key,
        boolean enabled,
        int quoteTtlSeconds,
        int connectTimeoutMs,
        int readTimeoutMs,
        int priority,
        BigDecimal partnerFeePercent,
        BigDecimal slippagePercent,
        int rateLimitRps,
        boolean prewarmOnStart
) {
    public SimpleSwapProperties {
        if (quoteTtlSeconds <= 0) quoteTtlSeconds = 60;
        if (connectTimeoutMs <= 0) connectTimeoutMs = 3000;
        if (readTimeoutMs <= 0) readTimeoutMs = 8000;
        if (priority <= 0) priority = 500;
        if (partnerFeePercent == null || partnerFeePercent.signum() < 0
                || partnerFeePercent.compareTo(BigDecimal.ONE) >= 0) {
            partnerFeePercent = BigDecimal.ZERO;
        }
        if (slippagePercent == null || slippagePercent.signum() <= 0
                || slippagePercent.compareTo(BigDecimal.ONE) >= 0) {
            slippagePercent = new BigDecimal("0.02");
        }
        if (rateLimitRps <= 0) rateLimitRps = 10;
    }

    public boolean isConfigured() {
        return url != null && !url.isBlank()
                && key != null && !key.isBlank()
                && enabled;
    }
}
