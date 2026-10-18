package com.example.walletbot.asset.application;

import com.example.walletbot.shared.constant.Asset;
import com.example.walletbot.shared.constant.SupportedCurrency;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PriceService {

    private final RestTemplate restTemplate;

    public PriceService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private volatile Map<String, CoinPrice> cachedPrices = new LinkedHashMap<>();
    private volatile Map<String, List<BigDecimal>> cachedSparklines = new LinkedHashMap<>();
    private volatile Map<String, BigDecimal> fiatRates = new HashMap<>();

    private static final String BINANCE_BASE = "https://api.binance.com";

    private static final String TICKER_24HR_URL = BINANCE_BASE + "/api/v3/ticker/24hr"
            + "?symbols=[\"BTCUSDT\",\"ETHUSDT\",\"BNBUSDT\",\"SOLUSDT\","
            + "\"TONUSDT\",\"TRXUSDT\"]"
            + "&type=MINI";

    private static final List<String> FIAT_RATES_URLS = List.of(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json",
            "https://latest.currency-api.pages.dev/v1/currencies/usd.json");

    private static final Map<String, String> SYMBOL_TO_PAIR = Map.ofEntries(
            Map.entry("BTC", "BTCUSDT"),
            Map.entry("ETH", "ETHUSDT"),
            Map.entry("BNB", "BNBUSDT"),
            Map.entry("SOL", "SOLUSDT"),
            Map.entry("GRAM", "TONUSDT"),
            Map.entry("TRX", "TRXUSDT"));

    private static final Map<String, String> PAIR_TO_SYMBOL = SYMBOL_TO_PAIR.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    private static final Map<String, String> SYMBOL_TO_NAME = Arrays.stream(Asset.values())
            .collect(Collectors.toUnmodifiableMap(Asset::getSymbol, Asset::getDisplayName, (a, b) -> a));

    public record CoinPrice(String symbol, String name,
                            BigDecimal price, BigDecimal change24h) {
    }

    @PostConstruct
    public void init() {
        updateFiatRates();
        updatePrices();
        updateMarketData();
    }

    private static boolean isStableSymbol(String symbol) {
        return "USDT".equalsIgnoreCase(symbol) || "USDC".equalsIgnoreCase(symbol);
    }

    @Scheduled(initialDelay = 600_000, fixedDelay = 600_000)
    public void updateMarketData() {
        Map<String, List<BigDecimal>> newSparklines = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : SYMBOL_TO_PAIR.entrySet()) {
            String symbol = entry.getKey();
            String pair = entry.getValue();
            try {
                List<BigDecimal> sparkline = fetchKlines(pair, "1d", 7).stream()
                        .map(k -> new BigDecimal(k.get(4).toString()))
                        .toList();
                newSparklines.put(symbol, sparkline);
            } catch (Exception e) {
                log.warn("Sparkline error for {}: {}", symbol, e.getMessage());
            }
        }
        newSparklines.put("USDT", Collections.nCopies(7, BigDecimal.ONE));
        newSparklines.put("USDC", Collections.nCopies(7, BigDecimal.ONE));

        cachedSparklines = newSparklines;
        log.debug("Sparklines updated for {} symbols", newSparklines.size());
    }

    @SuppressWarnings("unchecked")
    @Scheduled(initialDelay = 300_000, fixedDelay = 3_600_000)
    public void updateFiatRates() {
        for (String url : FIAT_RATES_URLS) {
            try {
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                Map<String, Object> rates = response == null
                        ? null : (Map<String, Object>) response.get("usd");
                if (rates == null || rates.isEmpty())
                    continue;

                Map<String, BigDecimal> newRates = new HashMap<>();
                rates.forEach((key, value) -> {
                    try {
                        newRates.put(
                                key.toLowerCase(Locale.ROOT),
                                new BigDecimal(value.toString()));
                    } catch (Exception ignored) {
                    }
                });
                if (!newRates.isEmpty()) {
                    fiatRates = newRates;
                    log.debug("Fiat rates updated from {}: {} currencies", url, newRates.size());
                    return;
                }
            } catch (Exception e) {
                log.warn("Fiat rate fetch failed from {}: {}", url, e.getMessage());
            }
        }
        log.error("All fiat rate sources failed; keeping {} cached rate(s)", fiatRates.size());
    }

    @SuppressWarnings("unchecked")
    List<List<Object>> fetchKlines(String pair, String interval, int limit) {
        String url = BINANCE_BASE + "/api/v3/klines"
                + "?symbol=" + pair + "&interval=" + interval + "&limit=" + limit;
        List<List<Object>> klines = restTemplate.getForObject(url, List.class);
        return klines != null ? klines : List.of();
    }

    public Map<String, BigDecimal> getUsdPrices(Collection<String> symbols) {
        Map<String, BigDecimal> out = new HashMap<>();
        for (String s : symbols)
            out.put(s, toUsd(s, BigDecimal.ONE).orElse(BigDecimal.ZERO));
        return out;
    }

    public Optional<String> getBinancePair(String symbol) {
        if (symbol == null)
            return Optional.empty();
        return Optional.ofNullable(SYMBOL_TO_PAIR.get(symbol.toUpperCase(Locale.ROOT)));
    }

    public Map<String, CoinPrice> getPrices() {
        return cachedPrices;
    }

    public Optional<BigDecimal> getUsdPriceBySymbol(String symbol) {
        if (symbol == null)
            return Optional.empty();
        CoinPrice cp = cachedPrices.get(symbol.toUpperCase(Locale.ROOT));
        if (cp == null || cp.price() == null)
            return Optional.empty();
        return Optional.of(cp.price());
    }

    public Optional<BigDecimal> toUsd(String symbol, BigDecimal amount) {
        if (amount == null)
            return Optional.empty();
        return getUsdPriceBySymbol(symbol).map(amount::multiply);
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 120_000)
    public void updatePrices() {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> response = restTemplate.getForObject(
                    TICKER_24HR_URL, List.class);
            if (response == null)
                return;

            Map<String, CoinPrice> newPrices = new LinkedHashMap<>();
            for (Map<String, Object> ticker : response) {
                String pair = (String) ticker.get("symbol");
                String symbol = PAIR_TO_SYMBOL.get(pair);
                if (symbol == null)
                    continue;

                BigDecimal price = new BigDecimal(ticker.get("lastPrice").toString());
                BigDecimal open = new BigDecimal(ticker.get("openPrice").toString());
                BigDecimal change = open.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : price.subtract(open).divide(open, 6, RoundingMode.HALF_UP)
                          .multiply(new BigDecimal("100"));
                String name = SYMBOL_TO_NAME.getOrDefault(symbol, symbol);

                newPrices.put(symbol, new CoinPrice(symbol, name, price, change));
            }

            newPrices.put("USDT", new CoinPrice("USDT", "Tether", BigDecimal.ONE, BigDecimal.ZERO));
            newPrices.put("USDC", new CoinPrice("USDC", "USD Coin", BigDecimal.ONE, BigDecimal.ZERO));

            cachedPrices = newPrices;
            log.debug("Prices updated: {} coins", newPrices.size());

        } catch (Exception e) {
            log.error("Binance price update error: ", e);
        }
    }

    public BigDecimal usdValue(String symbol, BigDecimal amount) {
        if (amount == null)
            return BigDecimal.ZERO;
        BigDecimal price = getUsdPriceBySymbol(symbol)
                .orElseGet(() -> isStableSymbol(symbol) ? BigDecimal.ONE : BigDecimal.ZERO);
        return amount.multiply(price);
    }

    public List<BigDecimal> getSparkline(String symbol) {
        if (symbol == null)
            return Collections.emptyList();
        return cachedSparklines.getOrDefault(
                symbol.toUpperCase(Locale.ROOT), Collections.emptyList());
    }

    private BigDecimal usdToCurrency(BigDecimal usd, SupportedCurrency currency) {
        if (currency == SupportedCurrency.usd)
            return usd;
        BigDecimal rate = fiatRates.get(currency.name());
        if (rate == null) {
            log.warn("Fiat rate for '{}' not found, falling back to USD", currency);
            return usd;
        }
        return usd.multiply(rate);
    }

    public FiatRatesView getSupportedFiatRates() {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        Map<String, String> symbols = new LinkedHashMap<>();
        for (SupportedCurrency c : SupportedCurrency.values()) {
            rates.put(c.name(), usdToCurrency(BigDecimal.ONE, c));
            symbols.put(c.name(), c.symbol());
        }
        return new FiatRatesView(rates, symbols);
    }

    public record FiatRatesView(Map<String, BigDecimal> rates, Map<String, String> symbols) {
    }
}