package com.example.walletbot.transaction.api.dto;

public record SendResponse(
        boolean success,
        String txHash,
        Long txId
) {
}
