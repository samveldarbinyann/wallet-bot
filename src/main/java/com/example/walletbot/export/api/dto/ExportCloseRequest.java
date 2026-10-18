package com.example.walletbot.export.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ExportCloseRequest(@NotBlank String exportSessionToken) {
}