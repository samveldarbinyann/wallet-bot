package com.example.walletbot.transaction.application;

import com.example.walletbot.shared.ratelimit.WalletActionLimiter;
import org.springframework.stereotype.Component;

@Component
public class SendRateLimiter extends WalletActionLimiter {

    private static final int MAX_SENDS_PER_WINDOW = 3;
    private static final long WINDOW_SECONDS = 300;
    private static final long WALLET_COOLDOWN_SECONDS = 60;

    public SendRateLimiter() {
        super(MAX_SENDS_PER_WINDOW, WINDOW_SECONDS, WALLET_COOLDOWN_SECONDS);
    }

    public void recordSend(Long userId, Long walletId) {
        record(userId, walletId);
    }
}
