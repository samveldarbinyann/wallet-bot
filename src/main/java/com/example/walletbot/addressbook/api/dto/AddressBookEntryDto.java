package com.example.walletbot.addressbook.api.dto;

public record AddressBookEntryDto(
        Long id,
        String network,
        String address,
        String label,
        String lastUsed
) {
}