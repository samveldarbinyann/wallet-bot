package com.example.walletbot.swap.application.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwapRateLimiterTest {

    private SwapRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new SwapRateLimiter();
    }

    @Test
    void isAllowed_freshUser_true() {
        assertThat(limiter.isAllowed(1L, 10L)).isTrue();
    }

    @Test
    void isAllowed_walletCooldownBlocksSameWallet() {
        limiter.record(1L, 10L);
        assertThat(limiter.isAllowed(1L, 10L)).isFalse();
    }

    @Test
    void isAllowed_differentWalletNotBlocked() {
        limiter.record(1L, 10L);
        assertThat(limiter.isAllowed(1L, 20L)).isTrue();
    }

    @Test
    void isAllowed_sixthSwapInWindowBlocked() {
        for (long w = 1; w <= 5; w++) {
            limiter.record(1L, w * 10);
        }
        assertThat(limiter.isAllowed(1L, 999L)).isFalse();
    }

    @Test
    void isAllowed_perUserIsolation() {
        for (long w = 1; w <= 5; w++) {
            limiter.record(1L, w * 10);
        }
        assertThat(limiter.isAllowed(2L, 999L)).isTrue();
    }
}
