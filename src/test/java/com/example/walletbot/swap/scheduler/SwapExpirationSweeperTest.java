package com.example.walletbot.swap.scheduler;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.swap.domain.SwapOrderEntity;
import com.example.walletbot.swap.domain.SwapOrderRepository;
import com.example.walletbot.swap.domain.SwapOrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwapExpirationSweeperTest {

    @Mock
    SwapOrderRepository orderRepository;
    @InjectMocks
    SwapExpirationSweeper sweeper;

    private SwapOrderEntity order(long id, SwapOrderStatus status, String sourceTxHash) {
        SwapOrderEntity o = new SwapOrderEntity();
        o.setId(id);
        o.setStatus(status);
        o.setSourceTxHash(sourceTxHash);
        o.setFromNetwork(BlockchainNetwork.BTC);
        o.setToNetwork(BlockchainNetwork.ETH);
        return o;
    }

    @Test
    void sweep_staleOrderWithoutSourceTx_expiredAndPersisted() {
        SwapOrderEntity stale = order(1L, SwapOrderStatus.AUTHORIZED, null);
        when(orderRepository.findByStatusInAndExpiresAtBefore(any(), any())).thenReturn(List.of(stale));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        sweeper.sweep();

        assertThat(stale.getStatus()).isEqualTo(SwapOrderStatus.EXPIRED);
        assertThat(stale.getStatusReason()).contains("expired");
        verify(orderRepository).saveAndFlush(stale);
    }

    @Test
    void sweep_orderWithSourceTxOnChain_neverForceExpired() {
        SwapOrderEntity broadcasting = order(2L, SwapOrderStatus.BROADCASTING, "0xalreadySent");
        when(orderRepository.findByStatusInAndExpiresAtBefore(any(), any())).thenReturn(List.of(broadcasting));

        sweeper.sweep();

        assertThat(broadcasting.getStatus()).isEqualTo(SwapOrderStatus.BROADCASTING);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void sweep_orderStuckAwaitingPayout_movedToRefundPending() {
        SwapOrderEntity stuck = order(3L, SwapOrderStatus.DEST_AWAITING, "0xsourceSent");
        when(orderRepository.findByStatusInAndExpiresAtBefore(any(), any())).thenReturn(List.of());
        when(orderRepository.findByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(stuck));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        sweeper.sweep();

        assertThat(stuck.getStatus()).isEqualTo(SwapOrderStatus.REFUND_PENDING);
        assertThat(stuck.getStatusReason()).contains("did not confirm payout");
        verify(orderRepository).saveAndFlush(stuck);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sweep_timeoutQueriesSourceConfirmedAndDestAwaitingBeforeCutoff() {
        when(orderRepository.findByStatusInAndExpiresAtBefore(any(), any())).thenReturn(List.of());
        when(orderRepository.findByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(List.of());

        sweeper.sweep();

        ArgumentCaptor<List<SwapOrderStatus>> statusCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(orderRepository).findByStatusInAndUpdatedAtBefore(statusCaptor.capture(), cutoffCaptor.capture());
        assertThat(statusCaptor.getValue())
                .containsExactlyInAnyOrder(SwapOrderStatus.SOURCE_CONFIRMED, SwapOrderStatus.DEST_AWAITING);
        assertThat(cutoffCaptor.getValue()).isBefore(Instant.now().minusSeconds(21000));
        assertThat(cutoffCaptor.getValue()).isAfter(Instant.now().minusSeconds(22200));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sweep_queriesOnlyAuthorizedAndBroadcastingBeforeCutoff() {
        when(orderRepository.findByStatusInAndExpiresAtBefore(any(), any())).thenReturn(List.of());

        sweeper.sweep();

        ArgumentCaptor<List<SwapOrderStatus>> statusCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(orderRepository).findByStatusInAndExpiresAtBefore(statusCaptor.capture(), cutoffCaptor.capture());
        assertThat(statusCaptor.getValue())
                .containsExactlyInAnyOrder(SwapOrderStatus.AUTHORIZED, SwapOrderStatus.BROADCASTING);
        assertThat(cutoffCaptor.getValue()).isBefore(Instant.now().minusSeconds(86000));
    }
}
