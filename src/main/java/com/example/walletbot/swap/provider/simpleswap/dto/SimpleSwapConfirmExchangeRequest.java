package com.example.walletbot.swap.provider.simpleswap.dto;

public record SimpleSwapConfirmExchangeRequest(
        String publicId,
        String txFrom,
        String providerUserDepositAddress,
        String targetAddress
) {
}
