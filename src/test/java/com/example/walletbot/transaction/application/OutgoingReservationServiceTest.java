package com.example.walletbot.transaction.application;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.transaction.domain.TransactionLogRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OutgoingReservationServiceTest {

    private final TransactionLogRepository repo = mock(TransactionLogRepository.class);
    private final OutgoingReservationService service = new OutgoingReservationService(repo);

    @Test
    void reservedOutgoing_sumsPendingFromRepo() {
        when(repo.sumReservedOutgoing(10L, BlockchainNetwork.USDT_ERC20))
                .thenReturn(new BigDecimal("12.5"));

        assertThat(service.reservedOutgoing(10L, BlockchainNetwork.USDT_ERC20))
                .isEqualByComparingTo("12.5");
    }

    @Test
    void reservedOutgoing_nullSum_returnsZero() {
        when(repo.sumReservedOutgoing(10L, BlockchainNetwork.ETH)).thenReturn(null);

        assertThat(service.reservedOutgoing(10L, BlockchainNetwork.ETH))
                .isEqualByComparingTo("0");
    }

    @Test
    void reservedOutgoing_nullArgs_returnsZeroWithoutQuery() {
        assertThat(service.reservedOutgoing(null, BlockchainNetwork.ETH))
                .isEqualByComparingTo("0");
        verifyNoInteractions(repo);
    }

    @Test
    void reservedOutgoingBatch_groupsRowsByWalletThenNetwork() {
        when(repo.sumReservedOutgoingByWallets(List.of(10L, 20L))).thenReturn(List.of(
                new Object[]{10L, BlockchainNetwork.ETH, new BigDecimal("1.5")},
                new Object[]{10L, BlockchainNetwork.USDT_ERC20, new BigDecimal("2.5")},
                new Object[]{20L, BlockchainNetwork.BTC, new BigDecimal("0.1")}));

        Map<Long, Map<BlockchainNetwork, BigDecimal>> result =
                service.reservedOutgoingBatch(List.of(10L, 20L));

        assertThat(result.get(10L).get(BlockchainNetwork.ETH)).isEqualByComparingTo("1.5");
        assertThat(result.get(10L).get(BlockchainNetwork.USDT_ERC20)).isEqualByComparingTo("2.5");
        assertThat(result.get(20L).get(BlockchainNetwork.BTC)).isEqualByComparingTo("0.1");
    }

    @Test
    void reservedOutgoingBatch_emptyWalletIds_returnsEmptyWithoutQuery() {
        assertThat(service.reservedOutgoingBatch(List.of())).isEmpty();
        verifyNoInteractions(repo);
    }

    @Test
    void broadcastBaseline_recordedThenReadBack() {
        service.recordBroadcastBaseline(10L, BlockchainNetwork.USDT_TRC20, new BigDecimal("100"));

        assertThat(service.pendingBaselineConfirmed(10L, BlockchainNetwork.USDT_TRC20))
                .isEqualByComparingTo("100");
    }

    @Test
    void pendingBaselineConfirmed_absent_returnsNull() {
        assertThat(service.pendingBaselineConfirmed(10L, BlockchainNetwork.USDT_TRC20)).isNull();
    }

    @Test
    void recordBroadcastBaseline_nullArgs_noOp() {
        service.recordBroadcastBaseline(null, BlockchainNetwork.ETH, new BigDecimal("5"));
        service.recordBroadcastBaseline(10L, BlockchainNetwork.ETH, null);

        assertThat(service.pendingBaselineConfirmed(10L, BlockchainNetwork.ETH)).isNull();
        assertThat(service.pendingBaselineConfirmed(null, BlockchainNetwork.ETH)).isNull();
    }

    @Test
    void recordBroadcastBaseline_secondCallWhileFirstStillCached_doesNotOverwrite() {
        service.recordBroadcastBaseline(10L, BlockchainNetwork.BNB, new BigDecimal("100"));
        service.recordBroadcastBaseline(10L, BlockchainNetwork.BNB, new BigDecimal("70"));

        assertThat(service.pendingBaselineConfirmed(10L, BlockchainNetwork.BNB))
                .isEqualByComparingTo("100");
    }

    @Test
    void invalidateBaseline_clearsCachedEntry() {
        service.recordBroadcastBaseline(10L, BlockchainNetwork.BNB, new BigDecimal("100"));

        service.invalidateBaseline(10L, BlockchainNetwork.BNB);

        assertThat(service.pendingBaselineConfirmed(10L, BlockchainNetwork.BNB)).isNull();
    }

    @Test
    void invalidateBaseline_nullArgs_noOp() {
        service.recordBroadcastBaseline(10L, BlockchainNetwork.BNB, new BigDecimal("100"));

        service.invalidateBaseline(null, BlockchainNetwork.BNB);
        service.invalidateBaseline(10L, null);

        assertThat(service.pendingBaselineConfirmed(10L, BlockchainNetwork.BNB))
                .isEqualByComparingTo("100");
    }

    @Test
    void effectiveAvailable_reservedBackToZero_invalidatesStaleBaseline() {
        service.recordBroadcastBaseline(10L, BlockchainNetwork.BNB, new BigDecimal("100"));
        when(repo.sumReservedOutgoing(10L, BlockchainNetwork.BNB)).thenReturn(BigDecimal.ZERO);

        service.effectiveAvailable(10L, BlockchainNetwork.BNB, new BigDecimal("40"));

        assertThat(service.pendingBaselineConfirmed(10L, BlockchainNetwork.BNB)).isNull();
    }

    @Test
    void effectiveAvailable_noReservation_passesConfirmedThrough() {
        when(repo.sumReservedOutgoing(10L, BlockchainNetwork.BNB)).thenReturn(BigDecimal.ZERO);

        assertThat(service.effectiveAvailable(10L, BlockchainNetwork.BNB, new BigDecimal("2")))
                .isEqualByComparingTo("2");
    }

    @Test
    void effectiveAvailable_reservedWithoutBaseline_subtractsOnce() {
        when(repo.sumReservedOutgoing(10L, BlockchainNetwork.BNB)).thenReturn(new BigDecimal("0.5"));

        assertThat(service.effectiveAvailable(10L, BlockchainNetwork.BNB, new BigDecimal("2")))
                .isEqualByComparingTo("1.5");
    }

    @Test
    void effectiveAvailable_chainAlreadyReflectsPendingSpend_usesBroadcastBaselineNotDoubleSubtracted() {
        service.recordBroadcastBaseline(10L, BlockchainNetwork.BNB, new BigDecimal("2"));
        when(repo.sumReservedOutgoing(10L, BlockchainNetwork.BNB)).thenReturn(new BigDecimal("1.5"));

        BigDecimal available = service.effectiveAvailable(10L, BlockchainNetwork.BNB, new BigDecimal("0.5"));

        assertThat(available).isEqualByComparingTo("0.5");
    }

    @Test
    void effectiveAvailable_chainNotYetCaughtUp_usesLiveConfirmedBalance() {
        service.recordBroadcastBaseline(10L, BlockchainNetwork.BNB, new BigDecimal("2"));
        when(repo.sumReservedOutgoing(10L, BlockchainNetwork.BNB)).thenReturn(new BigDecimal("1.5"));

        BigDecimal available = service.effectiveAvailable(10L, BlockchainNetwork.BNB, new BigDecimal("2"));

        assertThat(available).isEqualByComparingTo("0.5");
    }

    @Test
    void effectiveAvailable_nullConfirmed_returnsZero() {
        assertThat(service.effectiveAvailable(10L, BlockchainNetwork.BNB, null))
                .isEqualByComparingTo("0");
    }
}
