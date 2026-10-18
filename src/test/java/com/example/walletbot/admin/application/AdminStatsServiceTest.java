package com.example.walletbot.admin.application;

import com.example.walletbot.admin.domain.AdminStatsDto;
import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.constant.UserStatus;
import com.example.walletbot.swap.domain.SwapOrderRepository;
import com.example.walletbot.swap.provider.simpleswap.SimpleSwapProperties;
import com.example.walletbot.transaction.domain.TransactionLogRepository;
import com.example.walletbot.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminStatsServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    TransactionLogRepository transactionLogRepository;
    @Mock
    SwapOrderRepository swapOrderRepository;
    @Mock
    PriceService priceService;
    @Mock
    SimpleSwapProperties swapProperties;

    @InjectMocks
    AdminStatsService service;

    @BeforeEach
    void setUp() {
        when(userRepository.countUsersByStatus()).thenReturn(List.of(
                new Object[]{UserStatus.ACTIVE, 7L},
                new Object[]{UserStatus.BLOCKED, 2L},
                new Object[]{UserStatus.FROZEN, 1L}));
        when(priceService.getUsdPrices(any())).thenAnswer(inv -> {
            Set<String> symbols = inv.getArgument(0);
            Map<String, BigDecimal> prices = new HashMap<>();
            if (symbols.contains("BTC")) prices.put("BTC", new BigDecimal("60000"));
            return prices;
        });
        when(swapProperties.partnerFeePercent()).thenReturn(new BigDecimal("0.01"));
        when(transactionLogRepository.aggregateTurnoverTotals(any(), any())).thenReturn(List.of());
        when(swapOrderRepository.aggregateCompletedSwaps(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void getStats_userCountsPopulated() {
        AdminStatsDto stats = service.getStats("DAY");

        assertThat(stats.totalUsers()).isEqualTo(10L);
        assertThat(stats.activeUsers()).isEqualTo(7L);
        assertThat(stats.blockedUsers()).isEqualTo(2L);
        assertThat(stats.frozenUsers()).isEqualTo(1L);
        assertThat(stats.sendUsd()).isEqualByComparingTo("0");
        assertThat(stats.revenueUsd()).isEqualByComparingTo("0");
    }

    @Test
    void getStats_aggregatesSendAndDeposit() {
        Object[] sendRow = {"SEND", "BTC", 2L, new BigDecimal("0"), new BigDecimal("1")};
        Object[] depositRow = {"DEPOSIT", "BTC", 1L, new BigDecimal("100"), new BigDecimal("0")};
        when(transactionLogRepository.aggregateTurnoverTotals(any(), any()))
                .thenReturn(List.of(sendRow, depositRow));

        AdminStatsDto stats = service.getStats("DAY");

        assertThat(stats.sendCount()).isEqualTo(2L);
        assertThat(stats.sendUsd()).isEqualByComparingTo("60000");
        assertThat(stats.depositCount()).isEqualTo(1L);
        assertThat(stats.depositUsd()).isEqualByComparingTo("100");
    }

    @Test
    void getStats_completedSwapsDriveVolumeAndRevenue() {
        Object[] swapRow = {BlockchainNetwork.BTC, 3L, new BigDecimal("2")};
        when(swapOrderRepository.aggregateCompletedSwaps(any(), any(), any()))
                .thenReturn(List.<Object[]>of(swapRow));

        AdminStatsDto stats = service.getStats("MONTH");

        assertThat(stats.swapCount()).isEqualTo(3L);
        assertThat(stats.swapUsd()).isEqualByComparingTo("120000");
        assertThat(stats.revenueUsd()).isEqualByComparingTo("1200");
    }
}
