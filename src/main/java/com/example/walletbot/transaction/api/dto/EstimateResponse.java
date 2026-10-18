package com.example.walletbot.transaction.api.dto;

import com.example.walletbot.transaction.domain.NetworkFee;

import java.math.BigDecimal;

public record EstimateResponse(
        BigDecimal maxSendable,
        String networkSymbol,
        boolean hasEnoughGas,
        BigDecimal gasShortfall,
        String intentToken,
        BigDecimal recipientAmount,
        NetworkFee networkFee
) {
}
