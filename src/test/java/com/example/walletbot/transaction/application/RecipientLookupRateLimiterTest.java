package com.example.walletbot.transaction.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecipientLookupRateLimiterTest {

    private final RecipientLookupRateLimiter limiter = new RecipientLookupRateLimiter();

    @Test
    void isAllowedForEstimate_withoutMarkResolved_consumesBudgetEveryCall() {
        for (int i = 0; i < 30; i++) {
            assertThat(limiter.isAllowedForEstimate(1L, "nobody")).isTrue();
        }
        assertThat(limiter.isAllowedForEstimate(1L, "nobody")).isFalse();
    }

    @Test
    void isAllowedForEstimate_afterMarkResolved_reusesExemptionWithoutConsumingBudget() {
        assertThat(limiter.isAllowedForEstimate(1L, "bobby")).isTrue();
        limiter.markResolved(1L, "bobby");

        for (int i = 0; i < 29; i++) {
            assertThat(limiter.isAllowed(1L)).isTrue();
        }
        assertThat(limiter.isAllowed(1L)).isFalse();

        assertThat(limiter.isAllowedForEstimate(1L, "bobby")).isTrue();
    }

    @Test
    void markResolved_normalizesLikeRecipientResolver_soEquivalentFormsShareTheExemption() {
        assertThat(limiter.isAllowedForEstimate(1L, "Bobby")).isTrue();
        limiter.markResolved(1L, "Bobby");

        assertThat(limiter.isAllowedForEstimate(1L, "@bobby")).isTrue();
        assertThat(limiter.isAllowedForEstimate(1L, " BOBBY ")).isTrue();
    }

    @Test
    void isAllowedForEstimate_differentUsers_independentBuckets() {
        for (int i = 0; i < 30; i++) {
            assertThat(limiter.isAllowedForEstimate(1L, "nobody")).isTrue();
        }
        assertThat(limiter.isAllowedForEstimate(1L, "nobody")).isFalse();

        assertThat(limiter.isAllowedForEstimate(2L, "nobody")).isTrue();
    }
}