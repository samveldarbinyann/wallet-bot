package com.example.walletbot.platform.blockchain.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(10)
class TonCenterRateLimiterTest {

    @Test
    void nonPositiveRate_acquireIsNoOp() {
        TonCenterRateLimiter limiter = new TonCenterRateLimiter(0);

        long start = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) limiter.acquire();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(50L);
    }

    @Test
    void positiveRate_spacesConsecutiveAcquires() {
        TonCenterRateLimiter limiter = new TonCenterRateLimiter(50);

        long start = System.currentTimeMillis();
        limiter.acquire();
        limiter.acquire();
        limiter.acquire();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isGreaterThanOrEqualTo(35L);
    }
}
