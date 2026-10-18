package com.example.walletbot.transaction.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SendRateLimiterTest {

    private SendRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new SendRateLimiter();
    }

    @Test
    void isAllowed_freshUser_returnsTrue() {
        assertThat(limiter.isAllowed(1L, 10L)).isTrue();
    }

    @Test
    void isAllowed_walletCooldown_blocksSameWallet() {
        limiter.recordSend(1L, 10L);

        assertThat(limiter.isAllowed(1L, 10L)).isFalse();
    }

    @Test
    void isAllowed_differentWallet_notBlockedByCooldown() {
        limiter.recordSend(1L, 10L);

        assertThat(limiter.isAllowed(1L, 20L)).isTrue();
    }

    @Test
    void isAllowed_thirdSendWithinWindow_stillAllowed_fourthBlocked() {
        limiter.recordSend(1L, 10L);
        limiter.recordSend(1L, 20L);
        limiter.recordSend(1L, 30L);

        assertThat(limiter.isAllowed(1L, 40L)).isFalse();
    }

    @Test
    void isAllowed_perUserIsolation() {
        limiter.recordSend(1L, 10L);
        limiter.recordSend(1L, 20L);
        limiter.recordSend(1L, 30L);

        assertThat(limiter.isAllowed(2L, 99L)).isTrue();
    }
}
