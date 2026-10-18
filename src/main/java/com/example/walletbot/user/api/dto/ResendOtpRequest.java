package com.example.walletbot.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ResendOtpRequest(@NotBlank String pendingToken) {
}