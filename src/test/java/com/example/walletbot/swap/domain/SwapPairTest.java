package com.example.walletbot.swap.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwapPairTest {

    @Test
    void of_validPair_setsBothNetworks() {
        SwapPair pair = SwapPair.of(BlockchainNetwork.BTC, BlockchainNetwork.ETH);
        assertThat(pair.from()).isEqualTo(BlockchainNetwork.BTC);
        assertThat(pair.to()).isEqualTo(BlockchainNetwork.ETH);
    }

    @Test
    void of_sameNetwork_throws() {
        assertThatThrownBy(() -> SwapPair.of(BlockchainNetwork.BTC, BlockchainNetwork.BTC))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_nullFrom_throws() {
        assertThatThrownBy(() -> SwapPair.of(null, BlockchainNetwork.BTC))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_nullTo_throws() {
        assertThatThrownBy(() -> SwapPair.of(BlockchainNetwork.BTC, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
