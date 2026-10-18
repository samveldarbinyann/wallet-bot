package com.example.walletbot.swap.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SwapQuoteResponse(
        String quoteId,
        String intentToken,
        SwapRouteDto route,
        BigDecimal amountIn,
        BigDecimal amountOut,
        BigDecimal amountOutMin,
        BigDecimal rate,
        SwapFeeBreakdownDto fees,
        BigDecimal amountOutUsd,
        BigDecimal totalCostUsd,
        BigDecimal minAmount,
        String fromSymbol,
        String toSymbol,
        Instant validUntil
) {
}
