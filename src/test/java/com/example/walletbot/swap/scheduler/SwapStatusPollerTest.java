package com.example.walletbot.swap.scheduler;

import com.example.walletbot.swap.application.SwapStatusService;
import com.example.walletbot.swap.domain.SwapOrderEntity;
import com.example.walletbot.swap.domain.SwapOrderStatus;
import com.example.walletbot.swap.infrastructure.SwapResilienceConfig.ProviderRateLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SwapStatusPollerTest {

    @Mock
    SwapStatusService statusService;

    private SwapStatusPoller poller;

    @BeforeEach
    void setUp() {
        ProviderRateLimiterRegistry rateLimiters =
                new ProviderRateLimiterRegistry(Map.of("simpleswap", 100));
        poller = new SwapStatusPoller(statusService, rateLimiters);
        ReflectionTestUtils.setField(poller, "batchSize", 5);
    }

    @Test
    void poll_cappedAtBatchSize_processesOldestFirst() {
        List<SwapOrderEntity> active = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            SwapOrderEntity o = new SwapOrderEntity();
            o.setId((long) i);
            o.setProvider("simpleswap");
            o.setProviderOrderId("p" + i);
            o.setUpdatedAt(Instant.ofEpochSecond(1_000_000 + i));
            active.add(o);
        }
        when(statusService.findActive(any())).thenReturn(active);

        poller.poll();

        verify(statusService, times(5)).pollAndApply(any());
        for (int i = 0; i < 5; i++) {
            verify(statusService).pollAndApply(active.get(i));
        }
        for (int i = 5; i < 20; i++) {
            verify(statusService, never()).pollAndApply(active.get(i));
        }
    }

    @Test
    void poll_exhaustsRateLimit_breaksMidBatch() {
        ProviderRateLimiterRegistry tinyBudget =
                new ProviderRateLimiterRegistry(Map.of("simpleswap", 1));
        SwapStatusPoller starved = new SwapStatusPoller(statusService, tinyBudget);
        ReflectionTestUtils.setField(starved, "batchSize", 5);

        List<SwapOrderEntity> active = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            SwapOrderEntity o = new SwapOrderEntity();
            o.setId((long) i);
            o.setProvider("simpleswap");
            o.setProviderOrderId("p" + i);
            o.setStatus(SwapOrderStatus.SOURCE_CONFIRMED);
            o.setUpdatedAt(Instant.ofEpochSecond(1_000_000 + i));
            active.add(o);
        }
        when(statusService.findActive(any())).thenReturn(active);

        starved.poll();

        verify(statusService, times(1)).pollAndApply(any());
    }
}
