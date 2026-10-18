package com.example.walletbot.swap.application;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.swap.application.intent.SwapIntent;
import com.example.walletbot.swap.domain.SwapOrderEntity;
import com.example.walletbot.swap.domain.SwapOrderRepository;
import com.example.walletbot.swap.domain.SwapOrderStatus;
import com.example.walletbot.swap.domain.exception.SwapIdempotencyConflictException;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.WalletEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwapOrderMutatorTest {

    @Mock
    SwapOrderRepository orderRepository;
    @InjectMocks
    SwapOrderMutator mutator;

    private UserEntity user;
    private WalletEntity fromWallet;
    private WalletEntity toWallet;
    private SwapIntent intent;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(7L);

        fromWallet = new WalletEntity();
        fromWallet.setId(10L);
        fromWallet.setAddress("bcAddr");

        toWallet = new WalletEntity();
        toWallet.setId(20L);
        toWallet.setAddress("0xAddr");

        intent = new SwapIntent(
                "jti-1", 7L, BlockchainNetwork.BTC, BlockchainNetwork.ETH,
                new BigDecimal("0.1"), new BigDecimal("1.6"), new BigDecimal("1.55"),
                "simpleswap", "qid", "bcAddr", "0xAddr",
                Instant.now().plusSeconds(60));
    }

    @Test
    void createAuthorizedOrder_new_persistsAuthorizedWithMappedFields() {
        when(orderRepository.findByUserIdAndIdemKey(7L, "idem-1")).thenReturn(Optional.empty());
        when(orderRepository.findByIntentJti("jti-1")).thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        SwapOrderEntity order = mutator.createAuthorizedOrder(user, intent, fromWallet, toWallet, "idem-1");

        assertThat(order.getStatus()).isEqualTo(SwapOrderStatus.AUTHORIZED);
        assertThat(order.getUserId()).isEqualTo(7L);
        assertThat(order.getFromWalletId()).isEqualTo(10L);
        assertThat(order.getToWalletId()).isEqualTo(20L);
        assertThat(order.getIntentJti()).isEqualTo("jti-1");
        assertThat(order.getIdemKey()).isEqualTo("idem-1");
        assertThat(order.getRateQuoted()).isEqualByComparingTo("16");
    }

    @Test
    void createAuthorizedOrder_existingIdemKey_returnsExistingWithoutInsert() {
        SwapOrderEntity existing = new SwapOrderEntity();
        existing.setId(101L);
        when(orderRepository.findByUserIdAndIdemKey(7L, "idem-1")).thenReturn(Optional.of(existing));

        SwapOrderEntity order = mutator.createAuthorizedOrder(user, intent, fromWallet, toWallet, "idem-1");

        assertThat(order.getId()).isEqualTo(101L);
        verify(orderRepository, never()).saveAndFlush(any());
        verify(orderRepository, never()).findByIntentJti(any());
    }

    @Test
    void createAuthorizedOrder_intentJtiAlreadyUsed_throwsConflict() {
        when(orderRepository.findByUserIdAndIdemKey(7L, "idem-1")).thenReturn(Optional.empty());
        when(orderRepository.findByIntentJti("jti-1")).thenReturn(Optional.of(new SwapOrderEntity()));

        assertThatThrownBy(() -> mutator.createAuthorizedOrder(user, intent, fromWallet, toWallet, "idem-1"))
                .isInstanceOf(SwapIdempotencyConflictException.class);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createAuthorizedOrder_uniqueViolationRace_fallsBackToConcurrentlyInsertedRow() {
        SwapOrderEntity concurrent = new SwapOrderEntity();
        concurrent.setId(202L);
        when(orderRepository.findByIntentJti("jti-1")).thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
        when(orderRepository.findByUserIdAndIdemKey(7L, "idem-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrent));

        SwapOrderEntity order = mutator.createAuthorizedOrder(user, intent, fromWallet, toWallet, "idem-1");

        assertThat(order.getId()).isEqualTo(202L);
    }

    @Test
    void createAuthorizedOrder_uniqueViolationButNoRow_throwsConflict() {
        when(orderRepository.findByIntentJti("jti-1")).thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
        when(orderRepository.findByUserIdAndIdemKey(7L, "idem-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mutator.createAuthorizedOrder(user, intent, fromWallet, toWallet, "idem-1"))
                .isInstanceOf(SwapIdempotencyConflictException.class);
    }

    @Test
    void applyUpdate_mutatesManagedAndCopiesStateBackToDetached() {
        SwapOrderEntity detached = new SwapOrderEntity();
        detached.setId(55L);

        SwapOrderEntity managed = new SwapOrderEntity();
        managed.setId(55L);
        when(orderRepository.findById(55L)).thenReturn(Optional.of(managed));
        when(orderRepository.saveAndFlush(managed)).thenAnswer(inv -> inv.getArgument(0));

        mutator.applyUpdate(detached, o -> {
            o.setStatus(SwapOrderStatus.SOURCE_CONFIRMED);
            o.setSourceTxHash("0xdeadbeef");
        });

        assertThat(managed.getStatus()).isEqualTo(SwapOrderStatus.SOURCE_CONFIRMED);
        assertThat(detached.getStatus()).isEqualTo(SwapOrderStatus.SOURCE_CONFIRMED);
        assertThat(detached.getSourceTxHash()).isEqualTo("0xdeadbeef");
    }

    @Test
    void applyUpdate_orderVanished_throws() {
        SwapOrderEntity detached = new SwapOrderEntity();
        detached.setId(99L);
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mutator.applyUpdate(detached, o -> {
        })).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markFailed_persistsStatusAndReason() {
        SwapOrderEntity detached = new SwapOrderEntity();
        detached.setId(70L);
        SwapOrderEntity managed = new SwapOrderEntity();
        managed.setId(70L);
        when(orderRepository.findById(70L)).thenReturn(Optional.of(managed));
        when(orderRepository.saveAndFlush(managed)).thenAnswer(inv -> inv.getArgument(0));

        mutator.markFailed(detached, SwapOrderStatus.FAILED_PROVIDER, "provider down");

        assertThat(managed.getStatus()).isEqualTo(SwapOrderStatus.FAILED_PROVIDER);
        assertThat(managed.getStatusReason()).isEqualTo("provider down");
        assertThat(detached.getStatus()).isEqualTo(SwapOrderStatus.FAILED_PROVIDER);
    }

    @Test
    void markFailed_truncatesOverlongReason() {
        SwapOrderEntity detached = new SwapOrderEntity();
        detached.setId(71L);
        SwapOrderEntity managed = new SwapOrderEntity();
        managed.setId(71L);
        when(orderRepository.findById(71L)).thenReturn(Optional.of(managed));
        when(orderRepository.saveAndFlush(managed)).thenAnswer(inv -> inv.getArgument(0));

        mutator.markFailed(detached, SwapOrderStatus.FAILED_SOURCE, "x".repeat(600));

        assertThat(managed.getStatusReason()).hasSize(480);
    }

    @Test
    void markFailed_persistFailure_isSwallowed() {
        SwapOrderEntity detached = new SwapOrderEntity();
        detached.setId(72L);
        when(orderRepository.findById(72L)).thenThrow(new RuntimeException("db down"));

        mutator.markFailed(detached, SwapOrderStatus.FAILED_SOURCE, "reason");
    }
}
