package com.example.walletbot.swap.api.dto;

import java.math.BigDecimal;

public record SwapRangeResponse(
        String from,
        String to,
        BigDecimal min,
        BigDecimal max
) {
}
