package com.example.walletbot.user.api.dto;

import java.math.BigDecimal;
import java.util.Map;

public record FiatRatesResponse(
        Map<String, BigDecimal> rates,
        Map<String, String> symbols
) {
}
