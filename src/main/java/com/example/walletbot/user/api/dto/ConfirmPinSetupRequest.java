package com.example.walletbot.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmPinSetupRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "OTP code must be 6 digits") String otpCode,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "PIN must be exactly 6 digits") String pin
) {
}
