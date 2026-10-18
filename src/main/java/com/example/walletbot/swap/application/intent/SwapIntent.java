package com.example.walletbot.swap.application.intent;

import com.example.walletbot.shared.constant.BlockchainNetwork;

import java.math.BigDecimal;
import java.time.Instant;

public record SwapIntent(
        String jti,
        long userId,
        BlockchainNetwork fromNetwork,
        BlockchainNetwork toNetwork,
        BigDecimal amountIn,
        BigDecimal amountOutExpected,
        BigDecimal amountOutMin,
        String providerId,
        String providerQuoteId,
        String fromAddress,
        String toAddress,
        Instant validUntil
) {
}
