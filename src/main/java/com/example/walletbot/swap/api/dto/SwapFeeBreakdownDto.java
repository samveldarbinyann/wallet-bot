package com.example.walletbot.swap.api.dto;

import com.example.walletbot.transaction.domain.NetworkFee;

import java.math.BigDecimal;

public record SwapFeeBreakdownDto(
        BigDecimal providerFeePercent,
        BigDecimal providerFeeAmount,
        NetworkFee networkFee
) {
}
