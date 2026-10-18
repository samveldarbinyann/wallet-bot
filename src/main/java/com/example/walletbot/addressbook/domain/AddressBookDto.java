package com.example.walletbot.addressbook.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;

import java.time.LocalDateTime;

public record AddressBookDto(
        Long id,
        BlockchainNetwork network,
        String address,
        String label,
        LocalDateTime lastUsed
) {
}
