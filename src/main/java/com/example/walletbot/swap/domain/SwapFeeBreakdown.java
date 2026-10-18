package com.example.walletbot.swap.domain;

import java.math.BigDecimal;

public record SwapFeeBreakdown(
        BigDecimal providerFeePercent,
        BigDecimal providerFeeAmount
) {
    public static SwapFeeBreakdown zero() {
        return new SwapFeeBreakdown(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
