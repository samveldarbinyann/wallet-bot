package com.example.walletbot.transaction.domain;

import java.math.BigDecimal;

public record TransactionDto(
        String id,
        String type,
        BigDecimal amount,
        String symbol,
        String network,
        String status,
        String address,
        String txHash,
        String createdAt,
        BigDecimal usdValue
) {
}
