package com.example.walletbot.wallet.api.dto;

import com.example.walletbot.platform.web.validation.NetworkName;
import com.example.walletbot.platform.web.validation.RawAddressPattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ValidateAddressRequest(
        @NotBlank
        @Size(max = 128, message = "Address too long")
        @Pattern(regexp = RawAddressPattern.REGEX, message = RawAddressPattern.MESSAGE)
        String address,

        @NetworkName
        String network
) {
}