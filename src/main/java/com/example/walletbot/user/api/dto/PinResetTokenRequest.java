package com.example.walletbot.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public record PinResetTokenRequest(
        @NotBlank String pinPendingToken
) {
}