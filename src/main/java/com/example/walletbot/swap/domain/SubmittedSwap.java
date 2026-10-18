package com.example.walletbot.swap.domain;

public record SubmittedSwap(
        String providerId,
        String providerOrderId,
        String sourceTxHash
) {
}
