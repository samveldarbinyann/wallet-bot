package com.example.walletbot.admin.api.dto;

import java.math.BigDecimal;

public record AdminTransactionDto(
        Long id,
        Long userId,
        Long walletId,
        String type,
        String network,
        String fromAddress,
        String toAddress,
        BigDecimal amount,
        String symbol,
        String txHash,
        String externalRef,
        String status,
        String errorMessage,
        String createdAt,
        String txTimestamp,
        BigDecimal usdValue,
        BigDecimal feeUsd
) {
}