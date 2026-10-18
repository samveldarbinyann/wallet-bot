package com.example.walletbot.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SetupEmailRequest(
        @NotBlank @Email String email
) {
}