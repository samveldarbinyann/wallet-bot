package com.example.walletbot.admin.application;

import com.example.walletbot.admin.domain.AdminStatsDto;
import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.constant.UserStatus;
import com.example.walletbot.swap.domain.SwapOrderRepository;
import com.example.walletbot.swap.domain.SwapOrderStatus;
import com.example.walletbot.swap.provider.simpleswap.SimpleSwapProperties;
import com.example.walletbot.transaction.domain.TransactionLogEntity.TxType;
import com.example.walletbot.transaction.domain.TransactionLogRepository;
import com.example.walletbot.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserRepository userRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final SwapOrderRepository swapOrderRepository;
    private final PriceService priceService;
    private final SimpleSwapProperties swapProperties;

    public AdminStatsDto getStats(String period) {
        LocalDateTime from = resolveFrom(period);
        LocalDateTime to = LocalDateTime.now(ZoneOffset.UTC);

        List<Object[]> txRows = transactionLogRepository.aggregateTurnoverTotals(from, to);
        List<Object[]> swapRows = swapOrderRepository.aggregateCompletedSwaps(
                SwapOrderStatus.COMPLETED, from.toInstant(ZoneOffset.UTC), to.toInstant(ZoneOffset.UTC));

        Map<String, BigDecimal> prices = priceService.getUsdPrices(collectSymbols(txRows, swapRows));

        long sendCount = 0, depositCount = 0;
        BigDecimal sendUsd = BigDecimal.ZERO, depositUsd = BigDecimal.ZERO;
        for (Object[] row : txRows) {
            String symbol = row[1] != null ? row[1].toString() : "";
            long cnt = ((Number) row[2]).longValue();
            BigDecimal usd = toBd(row[3]).add(
                    prices.getOrDefault(symbol, BigDecimal.ZERO).multiply(toBd(row[4])));
            switch (TxType.valueOf(row[0].toString())) {
                case SEND -> {
                    sendCount += cnt;
                    sendUsd = sendUsd.add(usd);
                }
                case DEPOSIT -> {
                    depositCount += cnt;
                    depositUsd = depositUsd.add(usd);
                }
                case SWAP -> {}
            }
        }

        long swapCount = 0;
        BigDecimal swapUsd = BigDecimal.ZERO;
        for (Object[] row : swapRows) {
            BlockchainNetwork net = (BlockchainNetwork) row[0];
            swapCount += ((Number) row[1]).longValue();
            swapUsd = swapUsd.add(
                    prices.getOrDefault(net.getAsset().getSymbol(), BigDecimal.ZERO).multiply(toBd(row[2])));
        }

        BigDecimal revenueUsd = swapUsd.multiply(swapProperties.partnerFeePercent());

        Map<UserStatus, Long> statusCounts = userRepository.countUsersByStatus().stream()
                .collect(Collectors.toMap(row -> (UserStatus) row[0], row -> ((Number) row[1]).longValue()));
        long activeUsers = statusCounts.getOrDefault(UserStatus.ACTIVE, 0L);
        long blockedUsers = statusCounts.getOrDefault(UserStatus.BLOCKED, 0L);
        long frozenUsers = statusCounts.getOrDefault(UserStatus.FROZEN, 0L);

        return new AdminStatsDto(
                scale(sendUsd), sendCount,
                scale(swapUsd), swapCount,
                scale(depositUsd), depositCount,
                scale(revenueUsd),
                activeUsers + blockedUsers + frozenUsers,
                activeUsers,
                blockedUsers,
                frozenUsers);
    }

    private Set<String> collectSymbols(List<Object[]> txRows, List<Object[]> swapRows) {
        Set<String> symbols = new HashSet<>();
        for (Object[] row : txRows) {
            if (row[1] != null) symbols.add(row[1].toString());
        }
        for (Object[] row : swapRows) {
            symbols.add(((BlockchainNetwork) row[0]).getAsset().getSymbol());
        }
        return symbols;
    }

    private LocalDateTime resolveFrom(String period) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return switch (period != null ? period.toUpperCase() : "DAY") {
            case "WEEK" -> now.minusDays(7);
            case "MONTH" -> now.minusDays(30);
            case "ALL" -> LocalDateTime.of(2000, 1, 1, 0, 0);
            default -> now.minusDays(1);
        };
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        return new BigDecimal(o.toString());
    }
}
