package com.example.walletbot.swap.provider.simpleswap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimpleSwapCreateExchangeRequest(
        boolean fixed,
        String tickerFrom,
        String networkFrom,
        String tickerTo,
        String networkTo,
        String amount,
        String addressTo,
        String userRefundAddress
) {
}
