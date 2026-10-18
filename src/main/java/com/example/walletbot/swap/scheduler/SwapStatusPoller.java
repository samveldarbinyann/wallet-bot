package com.example.walletbot.swap.scheduler;

import com.example.walletbot.swap.application.SwapStatusService;
import com.example.walletbot.swap.domain.SwapOrderEntity;
import com.example.walletbot.swap.domain.SwapOrderStatus;
import com.example.walletbot.swap.infrastructure.SwapResilienceConfig.ProviderRateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "swap.scheduler.status-poller.enabled", havingValue = "true", matchIfMissing = true)
public class SwapStatusPoller {

    private final SwapStatusService statusService;
    private final ProviderRateLimiterRegistry rateLimiters;

    @Value("${swap.scheduler.status-poller.batch-size:5}")
    private int batchSize;

    private static Instant updatedAtOrEpoch(SwapOrderEntity o) {
        return o.getUpdatedAt() != null ? o.getUpdatedAt() : Instant.EPOCH;
    }

    @Scheduled(fixedDelayString = "${swap.scheduler.status-poller.delay-ms:60000}",
            initialDelayString = "${swap.scheduler.status-poller.initial-delay-ms:15000}")
    public void poll() {
        List<SwapOrderEntity> active = statusService.findActive(List.of(
                SwapOrderStatus.BROADCASTING,
                SwapOrderStatus.SOURCE_CONFIRMED,
                SwapOrderStatus.DEST_AWAITING,
                SwapOrderStatus.REFUND_PENDING));
        if (active.isEmpty()) return;
        int limit = Math.max(1, batchSize);
        List<SwapOrderEntity> batch = active.stream()
                .filter(order -> !statusService.isBackedOff(order.getId()))
                .sorted(Comparator.comparing(SwapStatusPoller::updatedAtOrEpoch))
                .limit(limit)
                .toList();
        log.debug("SwapStatusPoller: {} active, polling batch of {}", active.size(), batch.size());
        for (SwapOrderEntity order : batch) {
            if (!rateLimiters.tryAcquire(order.getProvider())) {
                log.debug("Poller paused: no rate-limit budget for {}", order.getProvider());
                break;
            }
            try {
                statusService.pollAndApply(order);
            } catch (Exception e) {
                log.warn("Status poll failed for order {}: {}", order.getId(), e.getMessage());
            }
        }
    }
}
