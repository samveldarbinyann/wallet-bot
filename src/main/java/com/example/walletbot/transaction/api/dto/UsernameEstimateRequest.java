package com.example.walletbot.transaction.api.dto;

import com.example.walletbot.platform.web.validation.NetworkName;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UsernameEstimateRequest(
        @NetworkName
        String network,

        @NotBlank
        @Size(max = 33, message = "Username too long")
        String username,

        @NotNull
        @DecimalMin(value = "0", inclusive = false, message = "Amount must be > 0")
        @Digits(integer = 20, fraction = 18)
        BigDecimal amount,

        Boolean max
) {
}