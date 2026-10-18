package com.example.walletbot.swap.domain;

import java.math.BigDecimal;

public record SwapStatusUpdate(
        SwapOrderStatus status,
        String destTxHash,
        BigDecimal amountOutActual,
        String reason
) {
    public static SwapStatusUpdate noChange() {
        return new SwapStatusUpdate(null, null, null, null);
    }

    public boolean hasChange() {
        return status != null;
    }
}
