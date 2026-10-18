package com.example.walletbot.swap.application.policy;

import com.example.walletbot.shared.ratelimit.WalletActionLimiter;
import org.springframework.stereotype.Component;

@Component
public class SwapRateLimiter extends WalletActionLimiter {

    private static final int MAX_PER_WINDOW = 5;
    private static final long WINDOW_SECONDS = 300;
    private static final long WALLET_COOLDOWN_SECONDS = 30;

    public SwapRateLimiter() {
        super(MAX_PER_WINDOW, WINDOW_SECONDS, WALLET_COOLDOWN_SECONDS);
    }
}