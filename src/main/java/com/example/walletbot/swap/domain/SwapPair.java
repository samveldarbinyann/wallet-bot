package com.example.walletbot.swap.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;

public record SwapPair(BlockchainNetwork from, BlockchainNetwork to) {

    public SwapPair {
        if (from == null || to == null) {
            throw new IllegalArgumentException("SwapPair networks must be non-null");
        }
        if (from == to) {
            throw new IllegalArgumentException("SwapPair networks must differ");
        }
    }

    public static SwapPair of(BlockchainNetwork from, BlockchainNetwork to) {
        return new SwapPair(from, to);
    }
}
