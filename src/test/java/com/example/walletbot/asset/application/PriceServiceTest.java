package com.example.walletbot.asset.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PriceServiceTest {

    private PriceService service;

    @BeforeEach
    void setUp() {
        service = new PriceService(mock(RestTemplate.class));
        ReflectionTestUtils.setField(service, "cachedPrices", Map.of(
                "BTC", new PriceService.CoinPrice("BTC", "Bitcoin", new BigDecimal("60000"), new BigDecimal("2")),
                "ETH", new PriceService.CoinPrice("ETH", "Ethereum", new BigDecimal("3000"), BigDecimal.ZERO)
        ));
        ReflectionTestUtils.setField(service, "fiatRates", Map.of(
                "eur", new BigDecimal("0.9"),
                "rub", new BigDecimal("90")
        ));
        ReflectionTestUtils.setField(service, "cachedSparklines", Map.of(
                "BTC", List.of(BigDecimal.ONE, BigDecimal.TEN)
        ));
    }

    @Test
    void getUsdPriceBySymbol_caseInsensitive() {
        assertThat(service.getUsdPriceBySymbol("btc")).contains(new BigDecimal("60000"));
    }

    @Test
    void getUsdPriceBySymbol_unknown_empty() {
        assertThat(service.getUsdPriceBySymbol("XRP")).isEmpty();
    }

    @Test
    void getUsdPriceBySymbol_null_empty() {
        assertThat(service.getUsdPriceBySymbol(null)).isEmpty();
    }

    @Test
    void toUsd_multipliesAmountByPrice() {
        assertThat(service.toUsd("BTC", new BigDecimal("2"))).contains(new BigDecimal("120000"));
    }

    @Test
    void usdValue_unknownStablecoin_defaultsToOne() {
        assertThat(service.usdValue("USDT", new BigDecimal("5"))).isEqualByComparingTo("5");
        assertThat(service.usdValue("USDC", new BigDecimal("3"))).isEqualByComparingTo("3");
    }

    @Test
    void usdValue_unknownNonStable_isZero() {
        assertThat(service.usdValue("XRP", new BigDecimal("5"))).isEqualByComparingTo("0");
    }

    @Test
    void usdValue_nullAmount_isZero() {
        assertThat(service.usdValue("BTC", null)).isEqualByComparingTo("0");
    }

    @Test
    void getSupportedFiatRates_buildsRateAndSymbolForEverySupportedCurrency() {
        PriceService.FiatRatesView view = service.getSupportedFiatRates();

        assertThat(view.rates().keySet()).containsExactlyInAnyOrder("usd", "rub", "eur", "amd", "gbp");
        assertThat(view.symbols().keySet()).containsExactlyInAnyOrder("usd", "rub", "eur", "amd", "gbp");
        assertThat(view.rates().get("usd")).isEqualByComparingTo("1");
        assertThat(view.rates().get("rub")).isEqualByComparingTo("90");
        assertThat(view.rates().get("amd")).isEqualByComparingTo("1");
        assertThat(view.symbols().get("usd")).isEqualTo("$");
        assertThat(view.symbols().get("eur")).isEqualTo("€");
    }

    @Test
    void getBinancePair_resolvesKnownSymbol() {
        assertThat(service.getBinancePair("btc")).contains("BTCUSDT");
    }

    @Test
    void getSparkline_unknownSymbol_emptyList() {
        assertThat(service.getSparkline("XRP")).isEmpty();
    }

    @Test
    void getSparkline_knownSymbol_returnsSeries() {
        assertThat(service.getSparkline("BTC")).hasSize(2);
    }
}
