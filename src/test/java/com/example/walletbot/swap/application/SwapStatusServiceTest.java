package com.example.walletbot.swap.application;

import com.example.walletbot.notification.application.NotificationService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.event.WalletDepositEvent;
import com.example.walletbot.swap.domain.SwapOrderEntity;
import com.example.walletbot.swap.domain.SwapOrderRepository;
import com.example.walletbot.swap.domain.SwapOrderStatus;
import com.example.walletbot.swap.domain.SwapStatusUpdate;
import com.example.walletbot.swap.domain.exception.SwapOrderNotFoundException;
import com.example.walletbot.swap.provider.SwapProvider;
import com.example.walletbot.swap.routing.ProviderRegistry;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SwapStatusServiceTest {

    @Mock
    SwapOrderRepository orderRepository;
    @Mock
    ProviderRegistry providerRegistry;
    @Mock
    NotificationService notificationService;
    @Mock
    ApplicationEventPublisher eventPublisher;
    @Mock
    SwapProvider provider;

    @InjectMocks
    SwapStatusService service;

    private UserEntity user(long id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    private SwapOrderEntity order(long id, Long userId, BigDecimal expected) {
        SwapOrderEntity o = new SwapOrderEntity();
        o.setId(id);
        o.setUserId(userId);
        o.setStatus(SwapOrderStatus.DEST_AWAITING);
        o.setAmountOutExpected(expected);
        o.setFromNetwork(BlockchainNetwork.ETH);
        o.setToNetwork(BlockchainNetwork.BTC);
        o.setUpdatedAt(Instant.now());
        return o;
    }

    @Test
    void get_unknownOrder_throwsNotFound() {
        when(orderRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(user(1L), 5L))
                .isInstanceOf(SwapOrderNotFoundException.class);
    }

    @Test
    void get_orderOwnedByAnotherUser_throwsNotFound() {
        SwapOrderEntity o = order(5L, 2L, BigDecimal.ONE);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.get(user(1L), 5L))
                .isInstanceOf(SwapOrderNotFoundException.class);
    }

    @Test
    void get_ownedOrderWithoutProviderId_returnsWithoutPolling() {
        SwapOrderEntity o = order(5L, 1L, BigDecimal.ONE);
        o.setProviderOrderId(null);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(o));

        SwapOrderEntity result = service.get(user(1L), 5L);

        assertThat(result).isSameAs(o);
    }

    @Test
    void isSwapPayout_trueWhenOrderAlreadyCreditsThisTx() {
        when(orderRepository.existsByToWalletIdAndDestTxHash(99L, "0xpaid")).thenReturn(true);

        assertThat(service.isSwapPayout(99L, "0xpaid")).isTrue();
    }

    @Test
    void isSwapPayout_falseWhenNoOrderReferencesTheTx() {
        when(orderRepository.existsByToWalletIdAndDestTxHash(99L, "0xdeposit")).thenReturn(false);

        assertThat(service.isSwapPayout(99L, "0xdeposit")).isFalse();
    }

    @Test
    void isSwapPayout_falseWhenWalletOrTxHashMissing_withoutTouchingRepository() {
        assertThat(service.isSwapPayout(null, "0xdeposit")).isFalse();
        assertThat(service.isSwapPayout(99L, null)).isFalse();
        verifyNoInteractions(orderRepository);
    }

    private SwapOrderEntity pollable(long id) {
        SwapOrderEntity o = order(id, 1L, new BigDecimal("2.00"));
        o.setProvider("simpleswap");
        o.setProviderOrderId("ex-" + id);
        return o;
    }

    @Test
    void pollAndApply_nullProviderOrderId_returnsEmpty() {
        SwapOrderEntity o = order(1L, 1L, BigDecimal.ONE);
        o.setProviderOrderId(null);

        assertThat(service.pollAndApply(o)).isEmpty();
        verifyNoInteractions(providerRegistry);
    }

    @Test
    void pollAndApply_providerDisappeared_returnsEmpty() {
        SwapOrderEntity o = pollable(1L);
        when(providerRegistry.byId("simpleswap")).thenReturn(Optional.empty());

        assertThat(service.pollAndApply(o)).isEmpty();
    }

    @Test
    void pollAndApply_trackThrows_returnsEmpty() {
        SwapOrderEntity o = pollable(1L);
        when(providerRegistry.byId("simpleswap")).thenReturn(Optional.of(provider));
        when(provider.track(any())).thenThrow(new RuntimeException("provider boom"));

        assertThat(service.pollAndApply(o)).isEmpty();
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void pollAndApply_noChange_doesNotPersist() {
        SwapOrderEntity o = pollable(1L);
        when(providerRegistry.byId("simpleswap")).thenReturn(Optional.of(provider));
        when(provider.track(any())).thenReturn(SwapStatusUpdate.noChange());

        assertThat(service.pollAndApply(o)).isEmpty();
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void pollAndApply_managedAlreadyTerminal_shortCircuitsWithoutOverwriting() {
        SwapOrderEntity detached = pollable(1L);
        SwapOrderEntity managed = pollable(1L);
        managed.setStatus(SwapOrderStatus.COMPLETED);
        when(providerRegistry.byId("simpleswap")).thenReturn(Optional.of(provider));
        when(provider.track(any())).thenReturn(
                new SwapStatusUpdate(SwapOrderStatus.DEST_AWAITING, null, null, null));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(managed));

        assertThat(service.pollAndApply(detached)).contains(managed);
        assertThat(managed.getStatus()).isEqualTo(SwapOrderStatus.COMPLETED);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void pollAndApply_terminalTransition_persistsAndNotifies() {
        SwapOrderEntity detached = pollable(1L);
        SwapOrderEntity managed = pollable(1L);
        managed.setAmountOutMin(new BigDecimal("1.90"));
        when(providerRegistry.byId("simpleswap")).thenReturn(Optional.of(provider));
        when(provider.track(any())).thenReturn(new SwapStatusUpdate(
                SwapOrderStatus.COMPLETED, "0xdest", new BigDecimal("2.05"), null));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(managed));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.pollAndApply(detached)).isPresent();
        assertThat(managed.getStatus()).isEqualTo(SwapOrderStatus.COMPLETED);
        assertThat(managed.getDestTxHash()).isEqualTo("0xdest");
        assertThat(managed.getAmountOutActual()).isEqualByComparingTo("2.05");
        verify(notificationService).send(eq(1L), any(), any(), anyString(), anyString(), any(Object[].class));
    }

    @Test
    void pollAndApply_completed_publishesWalletDepositEventToRefreshBalance() {
        SwapOrderEntity detached = pollable(1L);
        SwapOrderEntity managed = pollable(1L);
        when(providerRegistry.byId("simpleswap")).thenReturn(Optional.of(provider));
        when(provider.track(any())).thenReturn(new SwapStatusUpdate(
                SwapOrderStatus.COMPLETED, "0xdest", new BigDecimal("2.00"), null));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(managed));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.pollAndApply(detached);

        verify(eventPublisher).publishEvent(new WalletDepositEvent(1L));
    }

    @Test
    void pollAndApply_completedWithDestination_publishesCreditEvent() {
        SwapOrderEntity detached = pollable(1L);
        SwapOrderEntity managed = pollable(1L);
        managed.setToWalletId(77L);
        when(providerRegistry.byId("simpleswap")).thenReturn(Optional.of(provider));
        when(provider.track(any())).thenReturn(new SwapStatusUpdate(
                SwapOrderStatus.COMPLETED, "0xdest", new BigDecimal("2.05"), null));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(managed));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.pollAndApply(detached);

        verify(eventPublisher).publishEvent(new WalletDepositEvent(1L, 77L,
                BlockchainNetwork.BTC, new BigDecimal("2.05"), "0xdest"));
    }

    @Test
    void pollAndApply_failedTerminal_doesNotPublishDepositEvent() {
        SwapOrderEntity detached = pollable(1L);
        SwapOrderEntity managed = pollable(1L);
        when(providerRegistry.byId("simpleswap")).thenReturn(Optional.of(provider));
        when(provider.track(any())).thenReturn(new SwapStatusUpdate(
                SwapOrderStatus.FAILED_PROVIDER, null, null, "provider could not deliver"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(managed));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.pollAndApply(detached);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void pollAndApply_settledBelowSlippageFloor_recordsReason() {
        SwapOrderEntity detached = pollable(1L);
        SwapOrderEntity managed = pollable(1L);
        managed.setAmountOutMin(new BigDecimal("1.90"));
        when(providerRegistry.byId("simpleswap")).thenReturn(Optional.of(provider));
        when(provider.track(any())).thenReturn(new SwapStatusUpdate(
                SwapOrderStatus.COMPLETED, "0xdest", new BigDecimal("1.50"), null));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(managed));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.pollAndApply(detached);

        assertThat(managed.getStatusReason()).contains("below quoted minimum");
    }
}
