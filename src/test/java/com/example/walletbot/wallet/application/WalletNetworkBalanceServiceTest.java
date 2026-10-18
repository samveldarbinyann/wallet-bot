package com.example.walletbot.wallet.application;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.wallet.domain.WalletNetworkBalanceEntity;
import com.example.walletbot.wallet.domain.WalletNetworkBalanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletNetworkBalanceServiceTest {

    @Mock
    WalletNetworkBalanceRepository repository;

    @InjectMocks
    WalletNetworkBalanceService service;

    private WalletNetworkBalanceEntity entity(String balance, Instant updatedAt) {
        WalletNetworkBalanceEntity e = new WalletNetworkBalanceEntity();
        e.setWalletId(1L);
        e.setNetwork(BlockchainNetwork.ETH);
        e.setBalance(balance);
        e.setUpdatedAt(updatedAt);
        return e;
    }

    @Test
    void findFreshBalance_nonPositiveMaxAge_empty() {
        assertThat(service.findFreshBalance(1L, BlockchainNetwork.ETH, 0)).isEmpty();
        verify(repository, never()).findByWalletIdAndNetwork(any(), any());
    }

    @Test
    void findFreshBalance_freshEntry_returnsBalance() {
        when(repository.findByWalletIdAndNetwork(1L, BlockchainNetwork.ETH))
                .thenReturn(Optional.of(entity("1.5", Instant.now())));

        assertThat(service.findFreshBalance(1L, BlockchainNetwork.ETH, 60_000)).contains("1.5");
    }

    @Test
    void findFreshBalance_staleEntry_empty() {
        when(repository.findByWalletIdAndNetwork(1L, BlockchainNetwork.ETH))
                .thenReturn(Optional.of(entity("1.5", Instant.now().minus(10, ChronoUnit.MINUTES))));

        assertThat(service.findFreshBalance(1L, BlockchainNetwork.ETH, 60_000)).isEmpty();
    }

    @Test
    void findFreshBalance_noEntry_empty() {
        when(repository.findByWalletIdAndNetwork(1L, BlockchainNetwork.ETH)).thenReturn(Optional.empty());

        assertThat(service.findFreshBalance(1L, BlockchainNetwork.ETH, 60_000)).isEmpty();
    }

    @Test
    void upsertBalance_delegatesToAtomicUpsert() {
        service.upsertBalance(2L, BlockchainNetwork.BTC, "0.5");

        verify(repository).upsert(eq(2L), eq("BTC"), eq("0.5"), any(Instant.class));
        verify(repository, never()).save(any());
    }

    @Test
    void deleteAllForWalletIds_empty_noOp() {
        service.deleteAllForWalletIds(List.of());
        service.deleteAllForWalletIds(null);
        verify(repository, never()).deleteAllByWalletIdIn(any());
    }

    @Test
    void deleteAllForWalletIds_nonEmpty_delegates() {
        service.deleteAllForWalletIds(List.of(1L, 2L));
        verify(repository).deleteAllByWalletIdIn(List.of(1L, 2L));
    }
}
