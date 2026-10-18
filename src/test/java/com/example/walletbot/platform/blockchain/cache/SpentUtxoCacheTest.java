package com.example.walletbot.platform.blockchain.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpentUtxoCacheTest {

    private SpentUtxoCache cache;

    @BeforeEach
    void setUp() {
        cache = new SpentUtxoCache();
    }

    @Test
    void isAvailable_freshUtxo_true() {
        assertThat(cache.isAvailable("0xabc", 0)).isTrue();
    }

    @Test
    void markAsSpent_thenUnavailable() {
        cache.markAsSpent("0xabc", 0);
        assertThat(cache.isAvailable("0xabc", 0)).isFalse();
    }

    @Test
    void markAsSpent_isPerOutputIndex() {
        cache.markAsSpent("0xabc", 0);
        assertThat(cache.isAvailable("0xabc", 1)).isTrue();
    }

    @Test
    void markAsSpent_isPerTxHash() {
        cache.markAsSpent("0xabc", 0);
        assertThat(cache.isAvailable("0xdef", 0)).isTrue();
    }
}
