package com.example.walletbot.shared.constant;

public enum NotificationType {
    SEND_SUCCESS(false),
    SEND_FAILED(false),
    DEPOSIT(false),
    APP_UPDATE(false),
    NEWS(false),
    SWAP(false),
    ACCOUNT_FROZEN(true),
    ACCOUNT_UNFROZEN(true),
    ACCOUNT_BLOCKED(true),
    ACCOUNT_UNBLOCKED(true);

    private final boolean critical;

    NotificationType(boolean critical) {
        this.critical = critical;
    }

    public boolean isCritical() {
        return critical;
    }
}
