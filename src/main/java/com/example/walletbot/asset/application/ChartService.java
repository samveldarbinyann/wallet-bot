package com.example.walletbot.asset.application;

import com.example.walletbot.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {

    private static final Set<String> ALLOWED_DAYS = Set.of("1", "7", "30", "365", "max");

    private final PriceService priceService;

    @Cacheable(value = "chart",
            key = "#symbol.toUpperCase() + '_' + (#days == null ? '' : #days.trim().toLowerCase())",
            unless = "#result == null || #result.isEmpty()")
    public List<List<BigDecimal>> getChart(String symbol, String days) {
        String normalized = days == null ? "" : days.trim().toLowerCase();
        if (!ALLOWED_DAYS.contains(normalized))
            throw new BadRequestException("error.chart_invalid_days");
        String pair = priceService.getBinancePair(symbol).orElse(null);
        if (pair == null) return Collections.emptyList();

        ChartParams params = resolveParams(normalized);
        try {
            return priceService.fetchKlines(pair, params.interval(), params.limit()).stream()
                    .map(k -> List.of(
                            new BigDecimal(k.getFirst().toString()),
                            new BigDecimal(k.get(4).toString())
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("Chart retrieval error for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    private record ChartParams(String interval, int limit) {
    }

    private ChartParams resolveParams(String days) {
        return switch (days) {
            case "1" -> new ChartParams("1h", 24);
            case "30" -> new ChartParams("1d", 30);
            case "365" -> new ChartParams("1w", 52);
            case "max" -> new ChartParams("1M", 60);
            default -> new ChartParams("4h", 42);
        };
    }
}