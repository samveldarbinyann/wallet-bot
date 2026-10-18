package com.example.walletbot.export.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ExportRevealRequest(
        @NotBlank String exportSessionToken,
        @Positive long walletId
) {
}