package com.example.walletbot.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DisablePinRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "PIN must be exactly 6 digits") String currentPin
) {
}