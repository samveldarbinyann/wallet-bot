package com.example.walletbot.swap.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SwapExecuteRequest(@NotBlank String intentToken) {
}
