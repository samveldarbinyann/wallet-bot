package com.example.walletbot.addressbook.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateAddressBookLabelRequest(
        @Size(max = 100) String label
) {
}
