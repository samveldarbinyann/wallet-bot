package com.example.walletbot.swap.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SwapQuoteRequest(
        @NotBlank String fromNetwork,
        @NotBlank String toNetwork,
        @Positive BigDecimal amount
) {
}
