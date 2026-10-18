package com.example.walletbot.transaction.api.dto;

import com.example.walletbot.transaction.domain.NetworkFee;

import java.math.BigDecimal;

public record FeePreviewResponse(
        NetworkFee networkFee,
        boolean hasEnoughGas,
        String gasSymbol,
        BigDecimal minSend,
        BigDecimal minSendUsd
) {
}
