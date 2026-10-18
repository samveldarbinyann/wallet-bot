package com.example.walletbot.transaction.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SendRequest(
        @NotBlank String intentToken
) {
}
