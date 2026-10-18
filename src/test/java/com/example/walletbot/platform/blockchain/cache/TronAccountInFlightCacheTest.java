package com.example.walletbot.platform.blockchain.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TronAccountInFlightCacheTest {

    private TronAccountInFlightCache cache;

    @BeforeEach
    void setUp() {
        cache = new TronAccountInFlightCache();
    }

    @Test
    void dedup_returnsSupplierResult() {
        Map<String, Object> result = cache.dedup("addr", () -> Map.of("balance", 10));
        assertThat(result).containsEntry("balance", 10);
    }

    @Test
    void dedup_removesEntryAfterCompletionSoNextCallReFetches() {
        cache.dedup("addr", () -> Map.of("v", 1));
        Map<String, Object> second = cache.dedup("addr", () -> Map.of("v", 2));

        assertThat(second).containsEntry("v", 2);
    }

    @Test
    void dedup_propagatesSupplierException() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> cache.dedup("addr", () -> {
                    throw new IllegalStateException("rpc fail");
                })).hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
