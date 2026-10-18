package com.example.walletbot.shared.event;

import com.example.walletbot.shared.constant.BlockchainNetwork;

import java.math.BigDecimal;

public record WalletDepositEvent(
        Long userId, Long walletId, BlockchainNetwork network, BigDecimal amount, String txHash) {

    public WalletDepositEvent(Long userId) {
        this(userId, null, null, null, null);
    }

    public boolean hasCredit() {
        return walletId != null && network != null
                && amount != null && amount.signum() > 0 && txHash != null;
    }
}
