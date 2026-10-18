package com.example.walletbot.platform.web.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtpRateLimiterTest {

    @Test
    void isAllowed_threeAttemptsPass_fourthBlocked() {
        OtpRateLimiter limiter = new OtpRateLimiter();

        assertThat(limiter.isAllowed(1L)).isTrue();
        assertThat(limiter.isAllowed(1L)).isTrue();
        assertThat(limiter.isAllowed(1L)).isTrue();
        assertThat(limiter.isAllowed(1L)).isFalse();
    }

    @Test
    void isAllowed_perUserIsolation() {
        OtpRateLimiter limiter = new OtpRateLimiter();

        for (int i = 0; i < 3; i++) limiter.isAllowed(1L);

        assertThat(limiter.isAllowed(2L)).isTrue();
    }
}
