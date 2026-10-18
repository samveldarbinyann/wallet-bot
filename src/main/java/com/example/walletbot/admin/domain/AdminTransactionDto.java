package com.example.walletbot.admin.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.transaction.domain.TransactionLogEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminTransactionDto(
        Long id,
        Long userId,
        Long walletId,
        TransactionLogEntity.TxType type,
        BlockchainNetwork network,
        String fromAddress,
        String toAddress,
        BigDecimal amount,
        String symbol,
        String txHash,
        String externalRef,
        TransactionLogEntity.TxStatus status,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime txTimestamp,
        BigDecimal usdValue,
        BigDecimal feeUsd
) {
}
