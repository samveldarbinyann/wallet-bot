package com.example.walletbot.swap.infrastructure;

import com.example.walletbot.swap.provider.simpleswap.SimpleSwapProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class SwapResilienceConfig {

    @Bean
    public CircuitBreakerRegistry swapCircuitBreakerRegistry() {
        CircuitBreakerConfig defaults = CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(8)
                .failureRateThreshold(50f)
                .slowCallDurationThreshold(Duration.ofSeconds(6))
                .slowCallRateThreshold(70f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        return CircuitBreakerRegistry.of(defaults);
    }

    @Bean
    public ProviderRateLimiterRegistry providerRateLimiterRegistry(SimpleSwapProperties simpleSwapProperties) {
        return new ProviderRateLimiterRegistry(Map.of("simpleswap", simpleSwapProperties.rateLimitRps()));
    }

    public static class ProviderRateLimiterRegistry {
        private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
        private final Map<String, Integer> rps;

        public ProviderRateLimiterRegistry(Map<String, Integer> rps) {
            this.rps = rps;
        }

        public boolean tryAcquire(String provider) {
            int budget = rps.getOrDefault(provider, 5);
            Bucket bucket = buckets.computeIfAbsent(provider, p -> Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(budget)
                            .refillGreedy(budget, Duration.ofSeconds(1)).build())
                    .build());
            return bucket.tryConsume(1);
        }

        public CircuitBreaker breaker(CircuitBreakerRegistry registry, String provider) {
            return registry.circuitBreaker("swap-" + provider);
        }
    }
}
