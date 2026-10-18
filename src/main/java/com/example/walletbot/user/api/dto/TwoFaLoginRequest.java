package com.example.walletbot.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFaLoginRequest(
        @NotBlank String pendingToken,
        @NotBlank @Size(min = 6, max = 6) String code
) {
}