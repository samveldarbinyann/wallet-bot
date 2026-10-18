package com.example.walletbot.addressbook.api.dto;

import com.example.walletbot.platform.web.validation.RawAddressPattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveAddressBookEntryRequest(
        @NotBlank String network,
        @NotBlank
        @Size(max = 128, message = "Address too long")
        @Pattern(regexp = RawAddressPattern.REGEX, message = RawAddressPattern.MESSAGE)
        String address,
        @Size(max = 100) String label
) {
}
