package com.example.walletbot.swap.routing;

import com.example.walletbot.swap.domain.SwapPair;
import com.example.walletbot.swap.domain.SwapQuote;
import com.example.walletbot.swap.domain.SwapRanges;
import com.example.walletbot.swap.domain.exception.SwapException;
import com.example.walletbot.swap.domain.exception.SwapQuoteUnavailableException;
import com.example.walletbot.swap.infrastructure.SwapResilienceConfig.ProviderRateLimiterRegistry;
import com.example.walletbot.swap.provider.SwapProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SwapRouter {

    private final ProviderRegistry registry;
    private final CircuitBreakerRegistry breakerRegistry;
    private final ProviderRateLimiterRegistry rateLimiters;

    public SwapRouter(ProviderRegistry registry,
                      CircuitBreakerRegistry breakerRegistry,
                      ProviderRateLimiterRegistry rateLimiters) {
        this.registry = registry;
        this.breakerRegistry = breakerRegistry;
        this.rateLimiters = rateLimiters;
    }

    public SwapQuote selectBestQuote(SwapPair pair, BigDecimal amountIn) {
        List<SwapProvider> candidates = registry.supporting(pair);
        if (candidates.isEmpty()) {
            throw new SwapQuoteUnavailableException("No provider supports pair " + pair);
        }
        SwapException lastError = null;
        SwapQuote best = null;
        for (SwapProvider p : candidates) {
            CircuitBreaker breaker = rateLimiters.breaker(breakerRegistry, p.providerId());
            if (!breaker.tryAcquirePermission()) {
                log.debug("Provider {} circuit-open, skipping", p.providerId());
                continue;
            }
            if (!rateLimiters.tryAcquire(p.providerId())) {
                log.debug("Provider {} rate-limited, skipping", p.providerId());
                breaker.releasePermission();
                continue;
            }
            long start = System.nanoTime();
            try {
                SwapQuote q = p.quote(pair, amountIn);
                breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                if (best == null || q.amountOut().compareTo(best.amountOut()) > 0) {
                    best = q;
                }
            } catch (SwapException e) {
                breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
                log.warn("Provider {} declined quote for {}: {}", p.providerId(), pair, e.getMessage());
                lastError = e;
            } catch (RuntimeException e) {
                breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
                log.error("Provider {} threw unexpected error on quote", p.providerId(), e);
            }
        }
        if (best == null) {
            if (lastError != null) throw lastError;
            throw new SwapQuoteUnavailableException("All providers declined or were unavailable for " + pair);
        }
        return best;
    }

    public boolean supports(SwapPair pair) {
        return !registry.supporting(pair).isEmpty();
    }

    public Optional<SwapRanges> rangesFor(SwapPair pair) {
        for (SwapProvider p : registry.supporting(pair)) {
            CircuitBreaker breaker = rateLimiters.breaker(breakerRegistry, p.providerId());
            if (!breaker.tryAcquirePermission()) {
                log.debug("Provider {} circuit-open, skipping ranges for {}", p.providerId(), pair);
                continue;
            }
            long start = System.nanoTime();
            try {
                Optional<SwapRanges> r = p.ranges(pair);
                breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                if (r.isPresent()) {
                    return r;
                }
            } catch (RuntimeException e) {
                breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
                log.warn("Provider {} failed ranges for {}: {}", p.providerId(), pair, e.getMessage());
            }
        }
        return Optional.empty();
    }
}
