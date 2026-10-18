package com.example.walletbot.swap.application;

import com.example.walletbot.swap.application.intent.SwapIntent;
import com.example.walletbot.swap.domain.SwapOrderEntity;
import com.example.walletbot.swap.domain.SwapOrderRepository;
import com.example.walletbot.swap.domain.SwapOrderStatus;
import com.example.walletbot.swap.domain.exception.SwapIdempotencyConflictException;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.WalletEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwapOrderMutator {

    private final SwapOrderRepository orderRepository;

    @Transactional
    public SwapOrderEntity createAuthorizedOrder(UserEntity user,
                                                 SwapIntent intent,
                                                 WalletEntity fromWallet,
                                                 WalletEntity toWallet,
                                                 String idemKey) {
        var existing = orderRepository.findByUserIdAndIdemKey(user.getId(), idemKey);
        if (existing.isPresent()) return existing.get();

        if (orderRepository.findByIntentJti(intent.jti()).isPresent()) {
            throw new SwapIdempotencyConflictException("Intent token already used");
        }

        SwapOrderEntity order = new SwapOrderEntity();
        order.setUserId(user.getId());
        order.setFromWalletId(fromWallet.getId());
        order.setToWalletId(toWallet.getId());
        order.setFromNetwork(intent.fromNetwork());
        order.setToNetwork(intent.toNetwork());
        order.setFromAddress(fromWallet.getAddress());
        order.setToAddress(toWallet.getAddress());
        order.setAmountIn(intent.amountIn());
        order.setAmountOutExpected(intent.amountOutExpected());
        order.setAmountOutMin(intent.amountOutMin());
        order.setRateQuoted(intent.amountIn().signum() > 0
                ? intent.amountOutExpected().divide(intent.amountIn(), 18, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        order.setProvider(intent.providerId());
        order.setProviderQuoteId(intent.providerQuoteId());
        order.setIdemKey(idemKey);
        order.setIntentJti(intent.jti());
        order.setExpiresAt(intent.validUntil().plusSeconds(86400));
        order.setStatus(SwapOrderStatus.AUTHORIZED);
        try {
            return orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            return orderRepository.findByUserIdAndIdemKey(user.getId(), idemKey)
                    .orElseThrow(() -> new SwapIdempotencyConflictException(
                            "Idempotency conflict and order not found"));
        }
    }

    @Transactional
    public void applyUpdate(SwapOrderEntity order, Consumer<SwapOrderEntity> mutator) {
        SwapOrderEntity managed = orderRepository.findById(order.getId())
                .orElseThrow(() -> new IllegalStateException("Order vanished: " + order.getId()));
        mutator.accept(managed);
        SwapOrderEntity saved = orderRepository.saveAndFlush(managed);
        copyMutableState(saved, order);
    }

    @Transactional
    public void markFailed(SwapOrderEntity order, SwapOrderStatus status, String reason) {
        try {
            applyUpdate(order, o -> {
                o.setStatus(status);
                o.setStatusReason(SwapOrderEntity.truncateReason(reason));
            });
        } catch (Exception e) {
            log.error("Failed to persist failure state for order {}", order.getId(), e);
        }
    }

    private void copyMutableState(SwapOrderEntity src, SwapOrderEntity dst) {
        dst.setProviderOrderId(src.getProviderOrderId());
        dst.setDepositAddress(src.getDepositAddress());
        dst.setSourceTxHash(src.getSourceTxHash());
        dst.setDestTxHash(src.getDestTxHash());
        dst.setStatus(src.getStatus());
        dst.setStatusReason(src.getStatusReason());
        dst.setAmountOutActual(src.getAmountOutActual());
        dst.setUpdatedAt(src.getUpdatedAt());
        dst.setVersion(src.getVersion());
    }
}
