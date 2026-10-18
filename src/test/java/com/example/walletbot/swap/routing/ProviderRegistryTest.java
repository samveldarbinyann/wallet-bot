package com.example.walletbot.swap.routing;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.swap.domain.*;
import com.example.walletbot.swap.provider.SwapProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderRegistryTest {

    private static SwapProvider stub(String id, int prio, SwapPair supported) {
        return new SwapProvider() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public boolean supports(SwapPair pair) {
                return supported != null && supported.equals(pair);
            }

            @Override
            public int priority() {
                return prio;
            }

            @Override
            public SwapQuote quote(SwapPair pair, BigDecimal amountIn) {
                throw new UnsupportedOperationException();
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
        };
    }

    @Test
    void supporting_sortsByPriorityAsc() {
        SwapPair pair = SwapPair.of(BlockchainNetwork.BTC, BlockchainNetwork.ETH);
        SwapProvider p1 = stub("p1", 300, pair);
        SwapProvider p2 = stub("p2", 100, pair);
        SwapProvider p3 = stub("p3", 200, pair);
        ProviderRegistry reg = new ProviderRegistry(List.of(p1, p2, p3));
        assertThat(reg.supporting(pair))
                .extracting(SwapProvider::providerId)
                .containsExactly("p2", "p3", "p1");
    }

    @Test
    void byId_findsProvider() {
        SwapProvider p1 = stub("alpha", 100, null);
        ProviderRegistry reg = new ProviderRegistry(List.of(p1));
        assertThat(reg.byId("alpha")).contains(p1);
        assertThat(reg.byId("missing")).isEmpty();
    }

    @Test
    void supporting_filtersNonSupporting() {
        SwapPair pair = SwapPair.of(BlockchainNetwork.BTC, BlockchainNetwork.ETH);
        SwapPair other = SwapPair.of(BlockchainNetwork.TON, BlockchainNetwork.SOL);
        SwapProvider p1 = stub("p1", 100, pair);
        SwapProvider p2 = stub("p2", 200, other);
        ProviderRegistry reg = new ProviderRegistry(List.of(p1, p2));
        assertThat(reg.supporting(pair)).extracting(SwapProvider::providerId).containsExactly("p1");
    }
}
