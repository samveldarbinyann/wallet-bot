package com.example.walletbot.wallet.api.dto;

import com.example.walletbot.asset.domain.AssetItemDto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponse(
        BigDecimal totalBalance,
        BigDecimal change24hUsd,
        BigDecimal change24hPercent,
        List<AssetItemDto> assets
) {
}
