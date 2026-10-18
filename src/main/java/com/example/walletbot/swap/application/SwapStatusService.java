package com.example.walletbot.swap.application;

import com.example.walletbot.notification.application.NotificationService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.constant.NotificationType;
import com.example.walletbot.shared.event.WalletDepositEvent;
import com.example.walletbot.shared.util.AmountFormatter;
import com.example.walletbot.swap.domain.*;
import com.example.walletbot.swap.domain.exception.SwapOrderNotFoundException;
import com.example.walletbot.swap.provider.SwapProvider;
import com.example.walletbot.swap.routing.ProviderRegistry;
import com.example.walletbot.user.domain.UserEntity;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwapStatusService {

    private static final Duration POLL_COOLDOWN = Duration.ofSeconds(10);

    public static final Duration AWAITING_PAYOUT_TIMEOUT = Duration.ofHours(6);

    private static final int POLL_BACKOFF_FREE_FAILURES = 2;
    private static final Duration POLL_BACKOFF_BASE = Duration.ofMinutes(5);
    private static final Duration POLL_BACKOFF_MAX = Duration.ofMinutes(60);

    private final SwapOrderRepository orderRepository;
    private final ProviderRegistry providerRegistry;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    private final Cache<String, Instant> pollCooldown = Caffeine.newBuilder()
            .expireAfterWrite(POLL_COOLDOWN)
            .maximumSize(10_000)
            .build();

    private final Cache<Long, Integer> pollFailureCount = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(10_000)
            .build();

    private final Cache<Long, Instant> pollBackoffUntil = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(10_000)
            .build();

    public boolean isBackedOff(long orderId) {
        Instant until = pollBackoffUntil.getIfPresent(orderId);
        return until != null && Instant.now().isBefore(until);
    }

    private void recordPollFailure(long orderId) {
        int failures = pollFailureCount.asMap().merge(orderId, 1, Integer::sum);
        if (failures <= POLL_BACKOFF_FREE_FAILURES) return;
        int exponent = failures - POLL_BACKOFF_FREE_FAILURES - 1;
        Duration backoff = POLL_BACKOFF_BASE.multipliedBy(1L << Math.min(exponent, 10));
        if (backoff.compareTo(POLL_BACKOFF_MAX) > 0) backoff = POLL_BACKOFF_MAX;
        pollBackoffUntil.put(orderId, Instant.now().plus(backoff));
    }

    private void clearPollFailures(long orderId) {
        pollFailureCount.invalidate(orderId);
        pollBackoffUntil.invalidate(orderId);
    }

    public SwapOrderEntity get(UserEntity user, long orderId) {
        SwapOrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new SwapOrderNotFoundException(orderId));
        if (!order.getUserId().equals(user.getId())) {
            throw new SwapOrderNotFoundException(orderId);
        }
        if (order.getStatus().isActive() && order.getProviderOrderId() != null
                && shouldPoll(order.getProviderOrderId())) {
            pollAndApply(order);
        }
        return order;
    }

    private boolean shouldPoll(String providerOrderId) {
        return pollCooldown.asMap().putIfAbsent(providerOrderId, Instant.now()) == null;
    }

    public List<SwapOrderEntity> findActive(List<SwapOrderStatus> statuses) {
        return orderRepository.findByStatusIn(statuses);
    }

    public boolean isSwapPayout(Long toWalletId, String destTxHash) {
        return toWalletId != null && destTxHash != null
                && orderRepository.existsByToWalletIdAndDestTxHash(toWalletId, destTxHash);
    }

    private static final List<SwapOrderStatus> AWAITING_PAYOUT_STATUSES =
            List.of(SwapOrderStatus.SOURCE_CONFIRMED, SwapOrderStatus.DEST_AWAITING);

    public boolean hasPayoutAwaiting(Long toWalletId, BlockchainNetwork toNetwork) {
        return toWalletId != null && toNetwork != null
                && orderRepository.existsByToWalletIdAndToNetworkAndStatusIn(
                toWalletId, toNetwork, AWAITING_PAYOUT_STATUSES);
    }

    @Transactional
    public Optional<SwapOrderEntity> pollAndApply(SwapOrderEntity order) {
        if (order.getProviderOrderId() == null) return Optional.empty();
        SwapProvider provider = providerRegistry.byId(order.getProvider()).orElse(null);
        if (provider == null) {
            log.warn("Provider {} disappeared for order {}", order.getProvider(), order.getId());
            return Optional.empty();
        }
        SwapStatusUpdate update;
        try {
            update = provider.track(new SubmittedSwap(order.getProvider(),
                    order.getProviderOrderId(), order.getSourceTxHash()));
        } catch (Exception e) {
            log.warn("Provider track() failed for order {}: {}", order.getId(), e.getMessage());
            recordPollFailure(order.getId());
            return Optional.empty();
        }
        clearPollFailures(order.getId());
        if (!update.hasChange()) return Optional.empty();
        SwapOrderEntity managed = orderRepository.findById(order.getId()).orElse(null);
        if (managed == null) return Optional.empty();
        if (managed.getStatus().isTerminal()) return Optional.of(managed);

        managed.setStatus(update.status());
        if (update.destTxHash() != null) managed.setDestTxHash(update.destTxHash());
        String reason = update.reason();
        if (update.amountOutActual() != null) {
            managed.setAmountOutActual(update.amountOutActual());
            if (managed.getAmountOutMin() != null
                    && update.amountOutActual().compareTo(managed.getAmountOutMin()) < 0) {
                log.warn("Swap order {} settled below slippage floor: actual={} min={}",
                        managed.getId(), update.amountOutActual(), managed.getAmountOutMin());
                reason = "Settled below quoted minimum (" + update.amountOutActual().toPlainString()
                        + " < " + managed.getAmountOutMin().toPlainString() + ")";
            }
        }
        if (reason != null) managed.setStatusReason(SwapOrderEntity.truncateReason(reason));
        SwapOrderEntity saved = orderRepository.saveAndFlush(managed);
        if (saved.getStatus().isTerminal()) {
            notifyTerminal(saved);
            publishBalanceEvent(saved);
        }
        return Optional.of(saved);
    }

    private void publishBalanceEvent(SwapOrderEntity order) {
        if (order.getStatus() == SwapOrderStatus.COMPLETED
                && order.getToWalletId() != null && order.getDestTxHash() != null) {
            BigDecimal out = order.getAmountOutActual() != null
                    ? order.getAmountOutActual() : order.getAmountOutExpected();
            if (out != null && out.signum() > 0) {
                eventPublisher.publishEvent(new WalletDepositEvent(order.getUserId(),
                        order.getToWalletId(), order.getToNetwork(), out, order.getDestTxHash()));
                return;
            }
        }
        if (order.getStatus() == SwapOrderStatus.COMPLETED
                || order.getStatus() == SwapOrderStatus.REFUNDED) {
            eventPublisher.publishEvent(new WalletDepositEvent(order.getUserId()));
        }
    }

    private void notifyTerminal(SwapOrderEntity order) {
        try {
            String fromSymbol = order.getFromNetwork().getSymbol();
            String toSymbol = order.getToNetwork().getSymbol();
            Map<String, Object> data = Map.of("swapId", order.getId());
            switch (order.getStatus()) {
                case COMPLETED -> {
                    BigDecimal received = order.getAmountOutActual() != null
                            ? order.getAmountOutActual()
                            : order.getAmountOutExpected();
                    notificationService.send(order.getUserId(), NotificationType.SWAP, data,
                            "notification.swap.completed.title", "notification.swap.completed.body",
                            AmountFormatter.forDisplay(received), toSymbol);
                }
                case REFUNDED -> notificationService.send(order.getUserId(), NotificationType.SWAP, data,
                        "notification.swap.refunded.title", "notification.swap.refunded.body",
                        fromSymbol);
                case FAILED_SOURCE, FAILED_PROVIDER, REJECTED, EXPIRED ->
                        notificationService.send(order.getUserId(), NotificationType.SWAP, data,
                                "notification.swap.failed.title", "notification.swap.failed.body",
                                fromSymbol, toSymbol);
                default -> {
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send swap notification for order {}: {}", order.getId(), e.getMessage());
        }
    }
}
