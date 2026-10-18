package com.example.walletbot.swap.infrastructure;

import com.example.walletbot.swap.infrastructure.SwapResilienceConfig.ProviderRateLimiterRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderRateLimiterRegistryTest {

    @Test
    void tryAcquire_consumesBudgetThenThrottles() {
        ProviderRateLimiterRegistry reg = new ProviderRateLimiterRegistry(Map.of("simpleswap", 3));

        assertThat(reg.tryAcquire("simpleswap")).isTrue();
        assertThat(reg.tryAcquire("simpleswap")).isTrue();
        assertThat(reg.tryAcquire("simpleswap")).isTrue();
        assertThat(reg.tryAcquire("simpleswap")).isFalse();
    }

    @Test
    void tryAcquire_unknownProvider_usesDefaultBudgetOfFive() {
        ProviderRateLimiterRegistry reg = new ProviderRateLimiterRegistry(Map.of());

        for (int i = 0; i < 5; i++) {
            assertThat(reg.tryAcquire("mystery")).isTrue();
        }
        assertThat(reg.tryAcquire("mystery")).isFalse();
    }

    @Test
    void tryAcquire_budgetsAreIsolatedPerProvider() {
        ProviderRateLimiterRegistry reg = new ProviderRateLimiterRegistry(Map.of("a", 1, "b", 1));

        assertThat(reg.tryAcquire("a")).isTrue();
        assertThat(reg.tryAcquire("a")).isFalse();
        assertThat(reg.tryAcquire("b")).isTrue();
    }

    @Test
    void breaker_isNamespacedPerProvider() {
        ProviderRateLimiterRegistry reg = new ProviderRateLimiterRegistry(Map.of());
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();

        CircuitBreaker breaker = reg.breaker(cbRegistry, "simpleswap");

        assertThat(breaker.getName()).isEqualTo("swap-simpleswap");
        assertThat(reg.breaker(cbRegistry, "simpleswap")).isSameAs(breaker);
    }
}
