package com.example.walletbot.export.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExportConfirmRequest(
        @NotBlank String exportPendingToken,
        @NotBlank @Size(min = 6, max = 6) String otpCode
) {
}