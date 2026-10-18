package com.example.walletbot.shared.event;

import com.example.walletbot.shared.constant.BlockchainNetwork;

public record WalletCreatedEvent(
        Long userId,
        BlockchainNetwork network,
        String address
) {
}