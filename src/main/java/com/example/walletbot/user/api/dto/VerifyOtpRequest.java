package com.example.walletbot.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @NotBlank @Size(min = 6, max = 6) @Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits") String code
) {
}