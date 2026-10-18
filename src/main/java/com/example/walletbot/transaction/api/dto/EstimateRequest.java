package com.example.walletbot.transaction.api.dto;

import com.example.walletbot.platform.web.validation.NetworkName;
import com.example.walletbot.platform.web.validation.RawAddressPattern;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record EstimateRequest(
        @NetworkName
        String network,

        @NotBlank
        @Size(max = 128, message = "Address too long")
        @Pattern(regexp = RawAddressPattern.REGEX, message = RawAddressPattern.MESSAGE)
        String address,

        @NotNull
        @DecimalMin(value = "0", inclusive = false, message = "Amount must be > 0")
        @Digits(integer = 20, fraction = 18)
        BigDecimal amount,

        Boolean max
) {
}