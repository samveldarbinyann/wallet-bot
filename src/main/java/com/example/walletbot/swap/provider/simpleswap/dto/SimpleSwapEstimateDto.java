package com.example.walletbot.swap.provider.simpleswap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SimpleSwapEstimateDto(String estimatedAmount, String rateId, String validUntil) {
}
