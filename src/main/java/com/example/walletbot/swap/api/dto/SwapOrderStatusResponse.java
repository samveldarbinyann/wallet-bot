package com.example.walletbot.swap.api.dto;

import com.example.walletbot.swap.domain.SwapOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record SwapOrderStatusResponse(
        long orderId,
        SwapOrderStatus status,
        String statusReason,
        String provider,
        String fromNetwork,
        String toNetwork,
        String fromSymbol,
        String toSymbol,
        BigDecimal amountIn,
        BigDecimal amountOutExpected,
        BigDecimal amountOutActual,
        String sourceTxHash,
        String destTxHash,
        String depositAddress,
        Instant createdAt,
        Instant updatedAt
) {
}
