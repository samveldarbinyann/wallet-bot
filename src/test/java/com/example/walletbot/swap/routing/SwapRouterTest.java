package com.example.walletbot.swap.routing;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.swap.domain.*;
import com.example.walletbot.swap.domain.exception.SwapProviderUnavailableException;
import com.example.walletbot.swap.domain.exception.SwapQuoteUnavailableException;
import com.example.walletbot.swap.infrastructure.SwapResilienceConfig;
import com.example.walletbot.swap.provider.SwapProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwapRouterTest {

    private static SwapQuote quote(String providerId, SwapPair pair, BigDecimal amountOut) {
        return new SwapQuote(providerId, null, pair, BigDecimal.ONE, amountOut,
                amountOut, BigDecimal.ONE, SwapFeeBreakdown.zero(),
                Instant.now().plusSeconds(60), Map.of());
    }

    @Test
    void selectBestQuote_picksHigherAmountOut() {
        SwapPair pair = SwapPair.of(BlockchainNetwork.BTC, BlockchainNetwork.ETH);
        SwapProvider low = new StubProvider("low", 100, pair, quote("low", pair, new BigDecimal("0.5")));
        SwapProvider high = new StubProvider("high", 200, pair, quote("high", pair, new BigDecimal("0.8")));
        ProviderRegistry reg = new ProviderRegistry(List.of(low, high));
        SwapRouter router = new SwapRouter(reg, CircuitBreakerRegistry.ofDefaults(),
                new SwapResilienceConfig.ProviderRateLimiterRegistry(Map.of()));
        SwapQuote chosen = router.selectBestQuote(pair, BigDecimal.ONE);
        assertThat(chosen.providerId()).isEqualTo("high");
    }

    @Test
    void selectBestQuote_fallsBackWhenFirstFails() {
        SwapPair pair = SwapPair.of(BlockchainNetwork.BTC, BlockchainNetwork.ETH);
        SwapProvider broken = new ThrowingProvider("broken", 50, pair,
                new SwapProviderUnavailableException("down"));
        SwapProvider ok = new StubProvider("ok", 100, pair, quote("ok", pair, new BigDecimal("0.7")));
        ProviderRegistry reg = new ProviderRegistry(List.of(broken, ok));
        SwapRouter router = new SwapRouter(reg, CircuitBreakerRegistry.ofDefaults(),
                new SwapResilienceConfig.ProviderRateLimiterRegistry(Map.of()));
        SwapQuote chosen = router.selectBestQuote(pair, BigDecimal.ONE);
        assertThat(chosen.providerId()).isEqualTo("ok");
    }

    @Test
    void selectBestQuote_noSupportingProvider_throws() {
        SwapPair pair = SwapPair.of(BlockchainNetwork.BTC, BlockchainNetwork.ETH);
        ProviderRegistry reg = new ProviderRegistry(List.of());
        SwapRouter router = new SwapRouter(reg, CircuitBreakerRegistry.ofDefaults(),
                new SwapResilienceConfig.ProviderRateLimiterRegistry(Map.of()));
        assertThatThrownBy(() -> router.selectBestQuote(pair, BigDecimal.ONE))
                .isInstanceOf(SwapQuoteUnavailableException.class);
    }

    @Test
    void selectBestQuote_allFail_rethrowsLast() {
        SwapPair pair = SwapPair.of(BlockchainNetwork.BTC, BlockchainNetwork.ETH);
        SwapProvider a = new ThrowingProvider("a", 10, pair, new SwapProviderUnavailableException("a"));
        SwapProvider b = new ThrowingProvider("b", 20, pair, new SwapQuoteUnavailableException("b"));
        ProviderRegistry reg = new ProviderRegistry(List.of(a, b));
        SwapRouter router = new SwapRouter(reg, CircuitBreakerRegistry.ofDefaults(),
                new SwapResilienceConfig.ProviderRateLimiterRegistry(Map.of()));
        assertThatThrownBy(() -> router.selectBestQuote(pair, BigDecimal.ONE))
                .isInstanceOf(SwapQuoteUnavailableException.class);
    }

    private record StubProvider(String id, int prio, SwapPair pair, SwapQuote q) implements SwapProvider {
        @Override
        public String providerId() {
            return id;
        }

        @Override
        public boolean supports(SwapPair p) {
            return p.equals(pair);
        }

        @Override
        public int priority() {
            return prio;
        }

        @Override
        public SwapQuote quote(SwapPair p, BigDecimal a) {
            return q;
        }

        @Override
        public PreparedSwap prepare(SwapQuote q, String f, String t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SubmittedSwap submit(PreparedSwap p, WalletKeySource k) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SwapStatusUpdate track(SubmittedSwap s) {
            return SwapStatusUpdate.noChange();
        }
    }

    private record ThrowingProvider(String id, int prio, SwapPair pair, RuntimeException ex) implements SwapProvider {
        @Override
        public String providerId() {
            return id;
        }

        @Override
        public boolean supports(SwapPair p) {
            return p.equals(pair);
        }

        @Override
        public int priority() {
            return prio;
        }

        @Override
        public SwapQuote quote(SwapPair p, BigDecimal a) {
            throw ex;
        }

        @Override
        public PreparedSwap prepare(SwapQuote q, String f, String t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SubmittedSwap submit(PreparedSwap p, WalletKeySource k) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SwapStatusUpdate track(SubmittedSwap s) {
            return SwapStatusUpdate.noChange();
        }
    }
}
