package com.example.walletbot.swap.provider.simpleswap;

import com.example.walletbot.swap.domain.SwapRanges;
import com.example.walletbot.swap.provider.simpleswap.dto.SimpleSwapRangesDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimpleSwapCacheTest {

    private static final SimpleSwapCurrency FROM = new SimpleSwapCurrency("usdt", "eth");
    private static final SimpleSwapCurrency TO = new SimpleSwapCurrency("btc", "btc");

    private SimpleSwapClient client;
    private SimpleSwapCache cache;

    @BeforeEach
    void setUp() {
        client = mock(SimpleSwapClient.class);
        cache = new SimpleSwapCache(client);
    }

    @Test
    void ranges_mapsBothBounds() {
        when(client.getRanges(FROM, TO)).thenReturn(Optional.of(new SimpleSwapRangesDto("5", "9000")));

        Optional<SwapRanges> r = cache.ranges(FROM, TO);

        assertThat(r).isPresent();
        assertThat(r.get().min()).isEqualByComparingTo("5");
        assertThat(r.get().max()).isEqualByComparingTo("9000");
    }

    @Test
    void ranges_nullBoundsPreserved() {
        when(client.getRanges(FROM, TO)).thenReturn(Optional.of(new SimpleSwapRangesDto(null, null)));

        SwapRanges r = cache.ranges(FROM, TO).orElseThrow();

        assertThat(r.min()).isNull();
        assertThat(r.max()).isNull();
    }

    @Test
    void ranges_clientEmpty_empty() {
        when(client.getRanges(FROM, TO)).thenReturn(Optional.empty());
        assertThat(cache.ranges(FROM, TO)).isEmpty();
    }

    @Test
    void estimate_presentValue_returned() {
        when(client.getEstimated(any(), any(), any())).thenReturn(Optional.of(new BigDecimal("9.5")));
        assertThat(cache.estimate(FROM, TO, new BigDecimal("10"))).isEqualByComparingTo("9.5");
    }

    @Test
    void estimate_clientEmpty_collapsesToNull() {
        when(client.getEstimated(any(), any(), any())).thenReturn(Optional.empty());
        assertThat(cache.estimate(FROM, TO, new BigDecimal("10"))).isNull();
    }
}
