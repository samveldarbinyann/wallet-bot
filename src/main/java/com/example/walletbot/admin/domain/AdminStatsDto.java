package com.example.walletbot.admin.domain;

import java.math.BigDecimal;

public record AdminStatsDto(
        BigDecimal sendUsd,
        long sendCount,
        BigDecimal swapUsd,
        long swapCount,
        BigDecimal depositUsd,
        long depositCount,
        BigDecimal revenueUsd,
        long totalUsers,
        long activeUsers,
        long blockedUsers,
        long frozenUsers
) {
}
