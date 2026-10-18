package com.example.walletbot.asset.application;

import com.example.walletbot.shared.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChartServiceTest {

    @Mock
    PriceService priceService;

    @InjectMocks
    ChartService service;

    @Test
    void getChart_invalidDays_throws() {
        assertThatThrownBy(() -> service.getChart("BTC", "99"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.chart_invalid_days");
    }

    @Test
    void getChart_nullDays_throws() {
        assertThatThrownBy(() -> service.getChart("BTC", null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getChart_unknownSymbol_returnsEmpty() {
        when(priceService.getBinancePair("XRP")).thenReturn(Optional.empty());

        assertThat(service.getChart("XRP", "7")).isEmpty();
    }

    @Test
    void getChart_validRequest_mapsOpenTimeAndClose() {
        when(priceService.getBinancePair("BTC")).thenReturn(Optional.of("BTCUSDT"));
        List<List<Object>> klines = List.of(
                List.of(1000L, "10", "12", "9", "11", "100"),
                List.of(2000L, "11", "13", "10", "12", "200")
        );
        when(priceService.fetchKlines(eq("BTCUSDT"), any(), anyInt())).thenReturn(klines);

        List<List<BigDecimal>> chart = service.getChart("BTC", "7");

        assertThat(chart).hasSize(2);
        assertThat(chart.get(0).get(0)).isEqualByComparingTo("1000");
        assertThat(chart.get(0).get(1)).isEqualByComparingTo("11");
        assertThat(chart.get(1).get(1)).isEqualByComparingTo("12");
    }

    @Test
    void getChart_rpcError_returnsEmpty() {
        when(priceService.getBinancePair("BTC")).thenReturn(Optional.of("BTCUSDT"));
        when(priceService.fetchKlines(eq("BTCUSDT"), any(), anyInt())).thenThrow(new RuntimeException("boom"));

        assertThat(service.getChart("BTC", "1")).isEmpty();
    }
}
