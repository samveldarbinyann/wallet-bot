package com.example.walletbot.platform.web.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMutationRateLimiterTest {

    @Test
    void isAllowed_thirtyPass_thirtyFirstBlocked() {
        AdminMutationRateLimiter limiter = new AdminMutationRateLimiter();

        for (int i = 0; i < 30; i++) {
            assertThat(limiter.isAllowed(1L)).as("mutation %d", i).isTrue();
        }
        assertThat(limiter.isAllowed(1L)).isFalse();
    }

    @Test
    void isAllowed_perUserIsolation() {
        AdminMutationRateLimiter limiter = new AdminMutationRateLimiter();

        for (int i = 0; i < 30; i++) limiter.isAllowed(1L);

        assertThat(limiter.isAllowed(2L)).isTrue();
    }
}
