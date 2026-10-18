package com.example.walletbot.swap.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public record SwapQuote(
        String providerId,
        String providerQuoteId,
        SwapPair pair,
        BigDecimal amountIn,
        BigDecimal amountOut,
        BigDecimal amountOutMin,
        BigDecimal rate,
        SwapFeeBreakdown fees,
        Instant validUntil,
        Map<String, String> providerMeta
) {
    public SwapQuote {
        if (providerMeta == null) providerMeta = Collections.emptyMap();
        if (fees == null) fees = SwapFeeBreakdown.zero();
    }
}
