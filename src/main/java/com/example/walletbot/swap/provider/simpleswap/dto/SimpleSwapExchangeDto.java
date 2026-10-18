package com.example.walletbot.swap.provider.simpleswap.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SimpleSwapExchangeDto(
        @JsonAlias("publicId") String id,
        String status,
        String amountFrom,
        String amountTo,
        String addressFrom,
        String extraIdFrom,
        String txFrom,
        String txTo
) {
}
