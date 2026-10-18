package com.example.walletbot.transaction.domain;

import java.math.BigDecimal;

public record SwapHistoryDto(
        String id,
        String status,
        String statusReason,
        String fromSymbol,
        String toSymbol,
        String fromNetwork,
        String toNetwork,
        BigDecimal amountIn,
        BigDecimal amountOut,
        boolean amountOutSettled,
        String sourceTxHash,
        String destTxHash,
        String createdAt
) {
}
