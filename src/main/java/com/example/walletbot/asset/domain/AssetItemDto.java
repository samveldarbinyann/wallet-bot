package com.example.walletbot.asset.domain;

import java.math.BigDecimal;
import java.util.List;

public record AssetItemDto(
        String id,
        String networkId,
        String name,
        String symbol,
        BigDecimal balance,
        BigDecimal usdValue,
        BigDecimal currentPrice,
        BigDecimal priceChange24h,
        String address,
        String icon,
        List<BigDecimal> sparkline7d,
        BigDecimal pendingOutgoing
) {
}

