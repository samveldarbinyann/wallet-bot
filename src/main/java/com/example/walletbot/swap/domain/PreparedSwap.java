package com.example.walletbot.swap.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

public record PreparedSwap(
        String providerId,
        String providerOrderId,
        SwapPair pair,
        BigDecimal amountIn,
        String depositAddress,
        String memo,
        Map<String, String> meta
) {
    public PreparedSwap {
        if (meta == null) meta = Collections.emptyMap();
    }
}
