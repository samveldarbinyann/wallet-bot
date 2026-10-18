package com.example.walletbot.swap.provider.simpleswap;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleSwapCurrencyMapTest {

    private final SimpleSwapCurrencyMap map = new SimpleSwapCurrencyMap();

    private static final java.util.Set<BlockchainNetwork> UNSUPPORTED = java.util.EnumSet.of(
            BlockchainNetwork.BNB, BlockchainNetwork.USDT_BEP20, BlockchainNetwork.USDC_BEP20);

    @Test
    void supports_allConfiguredNetworks() {
        for (BlockchainNetwork n : BlockchainNetwork.values()) {
            if (UNSUPPORTED.contains(n)) {
                assertThat(map.supports(n)).as("%s is unsupported", n).isFalse();
                continue;
            }
            assertThat(map.supports(n))
                    .as("currency for %s", n)
                    .isTrue();
        }
    }

    @Test
    void currency_unmappedBscNetworks_throw() {
        for (BlockchainNetwork n : UNSUPPORTED) {
            assertThatThrownBy(() -> map.currency(n))
                    .as("currency(%s) throws", n)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void currency_returnsTickerAndNetwork() {
        assertThat(map.currency(BlockchainNetwork.BTC))
                .isEqualTo(new SimpleSwapCurrency("btc", "btc"));
        assertThat(map.currency(BlockchainNetwork.USDT_ERC20))
                .isEqualTo(new SimpleSwapCurrency("usdt", "eth"));
        assertThat(map.currency(BlockchainNetwork.USDT_TRC20))
                .isEqualTo(new SimpleSwapCurrency("usdt", "trx"));
        assertThat(map.currency(BlockchainNetwork.USDT_TON))
                .isEqualTo(new SimpleSwapCurrency("usdt", "ton"));
        assertThat(map.currency(BlockchainNetwork.USDC_ERC20))
                .isEqualTo(new SimpleSwapCurrency("usdc", "eth"));
    }
}
