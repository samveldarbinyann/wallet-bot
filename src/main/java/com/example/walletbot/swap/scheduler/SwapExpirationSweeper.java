package com.example.walletbot.swap.scheduler;

import com.example.walletbot.swap.application.SwapStatusService;
import com.example.walletbot.swap.domain.SwapOrderEntity;
import com.example.walletbot.swap.domain.SwapOrderRepository;
import com.example.walletbot.swap.domain.SwapOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "swap.scheduler.expiration-sweeper.enabled", havingValue = "true", matchIfMissing = true)
public class SwapExpirationSweeper {

    private final SwapOrderRepository orderRepository;

    @Scheduled(fixedDelayString = "${swap.scheduler.expiration-sweeper.delay-ms:60000}",
            initialDelayString = "${swap.scheduler.expiration-sweeper.initial-delay-ms:30000}")
    @Transactional
    public void sweep() {
        expireUnacknowledged();
        timeoutAwaitingPayout();
    }

    private void expireUnacknowledged() {
        Instant cutoff = Instant.now().minusSeconds(86400);
        List<SwapOrderEntity> stale = orderRepository.findByStatusInAndExpiresAtBefore(
                List.of(SwapOrderStatus.AUTHORIZED, SwapOrderStatus.BROADCASTING), cutoff);
        for (SwapOrderEntity order : stale) {
            if (order.getSourceTxHash() != null) {
                continue;
            }
            order.setStatus(SwapOrderStatus.EXPIRED);
            order.setStatusReason("Order expired without provider acknowledgement");
            orderRepository.saveAndFlush(order);
            log.info("Marked stale swap order {} as EXPIRED", order.getId());
        }
    }

    private void timeoutAwaitingPayout() {
        Instant cutoff = Instant.now().minus(SwapStatusService.AWAITING_PAYOUT_TIMEOUT);
        List<SwapOrderEntity> stuck = orderRepository.findByStatusInAndUpdatedAtBefore(
                List.of(SwapOrderStatus.SOURCE_CONFIRMED, SwapOrderStatus.DEST_AWAITING), cutoff);
        for (SwapOrderEntity order : stuck) {
            order.setStatus(SwapOrderStatus.REFUND_PENDING);
            order.setStatusReason("Provider did not confirm payout within "
                    + SwapStatusService.AWAITING_PAYOUT_TIMEOUT.toHours() + "h; awaiting refund");
            orderRepository.saveAndFlush(order);
            log.warn("Swap order {} stuck awaiting payout; moved to REFUND_PENDING", order.getId());
        }
    }
}
