package com.example.walletbot.transaction.domain;

import java.math.BigDecimal;

public record NetworkFee(
        BigDecimal amount,
        String symbol,
        BigDecimal usdValue
) {
}
