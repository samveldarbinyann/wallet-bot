package com.example.walletbot.swap.domain;

public enum SwapOrderStatus {
    QUOTED,
    AUTHORIZED,
    BROADCASTING,
    SOURCE_CONFIRMED,
    DEST_AWAITING,
    COMPLETED,
    REJECTED,
    EXPIRED,
    FAILED_SOURCE,
    FAILED_PROVIDER,
    REFUND_PENDING,
    REFUNDED;

    public boolean isTerminal() {
        return this == COMPLETED
                || this == REJECTED
                || this == EXPIRED
                || this == FAILED_SOURCE
                || this == FAILED_PROVIDER
                || this == REFUNDED;
    }

    public boolean isActive() {
        return !isTerminal();
    }
}
