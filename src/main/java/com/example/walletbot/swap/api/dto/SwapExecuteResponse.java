package com.example.walletbot.swap.api.dto;

import com.example.walletbot.swap.domain.SwapOrderStatus;

import java.math.BigDecimal;

public record SwapExecuteResponse(
        long orderId,
        SwapOrderStatus status,
        String provider,
        String fromSymbol,
        String toSymbol,
        BigDecimal amountIn,
        BigDecimal amountOutExpected,
        String sourceTxHash,
        String depositAddress
) {
}
