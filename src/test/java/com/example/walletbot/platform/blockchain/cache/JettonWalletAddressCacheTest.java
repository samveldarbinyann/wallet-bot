package com.example.walletbot.platform.blockchain.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JettonWalletAddressCacheTest {

    private JettonWalletAddressCache cache;

    @BeforeEach
    void setUp() {
        cache = new JettonWalletAddressCache();
    }

    @Test
    void resolve_callsResolverOnceThenCaches() {
        AtomicInteger calls = new AtomicInteger();

        String first = cache.resolve("owner", o -> {
            calls.incrementAndGet();
            return "jetton-" + o;
        });
        String second = cache.resolve("owner", o -> {
            calls.incrementAndGet();
            return "different";
        });

        assertThat(first).isEqualTo("jetton-owner");
        assertThat(second).isEqualTo("jetton-owner");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void resolve_nullResult_notCached() {
        AtomicInteger calls = new AtomicInteger();

        cache.resolve("owner", o -> {
            calls.incrementAndGet();
            return null;
        });
        cache.resolve("owner", o -> {
            calls.incrementAndGet();
            return "now-resolved";
        });

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void invalidate_forcesReResolution() {
        cache.resolve("owner", o -> "v1");
        cache.invalidate("owner");

        String afterInvalidate = cache.resolve("owner", o -> "v2");
        assertThat(afterInvalidate).isEqualTo("v2");
    }
}
