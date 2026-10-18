package com.example.walletbot.asset.application;

import com.example.walletbot.asset.domain.OutgoingReservationPort;
import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.wallet.application.WalletNetworkBalanceService;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.domain.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssetServiceTest {

    private BlockchainServiceFactory factory;
    private PriceService priceService;
    private WalletRepository walletRepository;
    private WalletNetworkBalanceService balanceService;
    private WalletService walletService;
    private ExecutorService executor;
    private BlockchainService blockchainService;
    private OutgoingReservationPort reservationPort;

    private AssetService service;

    @BeforeEach
    void setUp() {
        factory = mock(BlockchainServiceFactory.class);
        priceService = mock(PriceService.class);
        walletRepository = mock(WalletRepository.class);
        balanceService = mock(WalletNetworkBalanceService.class);
        walletService = mock(WalletService.class);
        executor = Executors.newSingleThreadExecutor();
        blockchainService = mock(BlockchainService.class);
        reservationPort = mock(OutgoingReservationPort.class);

        service = new AssetService(factory, priceService, walletRepository,
                balanceService, walletService, executor, reservationPort);

        when(factory.getService(any())).thenReturn(blockchainService);
        when(reservationPort.effectiveConfirmed(any(), any(), any(), any())).thenAnswer(inv -> {
            BigDecimal confirmed = inv.getArgument(2);
            return confirmed != null ? confirmed.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        });
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void getBalance_nullArgs_fetchesDirectly() {
        when(blockchainService.getBalance("0xABC")).thenReturn("3.0");

        String result = service.getBalance(null, null, "0xABC", BlockchainNetwork.ETH);

        assertThat(result).isEqualTo("3.0");
    }

    @Test
    void getBalance_dbFreshBalance_returnsWithoutFetching() {
        when(balanceService.findFreshBalance(eq(1L), eq(BlockchainNetwork.ETH), anyLong()))
                .thenReturn(Optional.of("2.0"));

        String result = service.getBalance(1L, 1L, "0xABC", BlockchainNetwork.ETH);

        assertThat(result).isEqualTo("2.0");
        verify(factory, never()).getService(any());
    }

    @Test
    void getBalance_noCacheNoDb_fetchesUpsertsAndCaches() {
        when(balanceService.findFreshBalance(anyLong(), any(), anyLong())).thenReturn(Optional.empty());
        when(blockchainService.getBalance("0xABC")).thenReturn("1.5");

        String first = service.getBalance(1L, 1L, "0xABC", BlockchainNetwork.ETH);
        String second = service.getBalance(1L, 1L, "0xABC", BlockchainNetwork.ETH);

        assertThat(first).isEqualTo("1.5");
        assertThat(second).isEqualTo("1.5");
        verify(blockchainService, times(1)).getBalance("0xABC");
        verify(balanceService, times(1)).upsertBalance(1L, BlockchainNetwork.ETH, "1.5");
    }

    @Test
    void getBalance_fetchFails_servesLastKnownDbValueWithoutCaching() {
        when(balanceService.findFreshBalance(anyLong(), any(), anyLong())).thenReturn(Optional.empty());
        when(blockchainService.getBalance("0xABC")).thenThrow(new BlockchainRpcException("503"));
        when(balanceService.findLastKnownBalance(1L, BlockchainNetwork.ETH))
                .thenReturn(Optional.of("5.5"));

        assertThat(service.getBalance(1L, 1L, "0xABC", BlockchainNetwork.ETH)).isEqualTo("5.5");
        verify(balanceService, never()).upsertBalance(anyLong(), any(), anyString());

        reset(blockchainService);
        when(blockchainService.getBalance("0xABC")).thenReturn("7.0");
        assertThat(service.getBalance(1L, 1L, "0xABC", BlockchainNetwork.ETH)).isEqualTo("7.0");
    }

    @Test
    void getBalance_fetchFails_noLastKnownValue_returnsZero() {
        when(balanceService.findFreshBalance(anyLong(), any(), anyLong())).thenReturn(Optional.empty());
        when(blockchainService.getBalance(any())).thenThrow(new BlockchainRpcException("503"));
        when(balanceService.findLastKnownBalance(1L, BlockchainNetwork.ETH))
                .thenReturn(Optional.empty());

        String result = service.getBalance(1L, 1L, "0xABC", BlockchainNetwork.ETH);

        assertThat(result).isEqualTo("0");
        verify(balanceService, never()).upsertBalance(anyLong(), any(), anyString());
    }

    @Test
    void creditBalance_mustRunInItsOwnTransaction() throws NoSuchMethodException {
        Transactional tx = AssetService.class
                .getMethod("creditBalance", Long.class, Long.class, BlockchainNetwork.class, BigDecimal.class)
                .getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.propagation())
                .as("creditBalance is invoked from an AFTER_COMMIT listener; REQUIRED would join the "
                        + "already-committed tx and the balance upsert would fail with TransactionRequiredException")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void getFreshBalance_alwaysFetches() {
        when(blockchainService.getBalance("0xABC")).thenReturn("9.9");

        assertThat(service.getFreshBalance("0xABC", BlockchainNetwork.ETH)).isEqualTo("9.9");
    }

    @Test
    void invalidateUserBalances_null_noOp() {
        service.invalidateUserBalances(null);
        verify(walletRepository, never()).findByUserEntityId(any());
    }

    @Test
    void invalidateUserBalances_clearsDbBalancesAndEvictsCache() {
        WalletEntity w = new WalletEntity();
        w.setId(10L);
        w.setNetwork(BlockchainNetwork.ETH);
        when(walletRepository.findByUserEntityId(1L)).thenReturn(List.of(w));
        when(balanceService.findFreshBalance(anyLong(), any(), anyLong()))
                .thenReturn(Optional.empty());
        when(blockchainService.getBalance("0xABC")).thenReturn("5.0", "7.0");

        assertThat(service.getBalance(1L, 10L, "0xABC", BlockchainNetwork.ETH)).isEqualTo("5.0");
        assertThat(service.getBalance(1L, 10L, "0xABC", BlockchainNetwork.ETH)).isEqualTo("5.0");
        verify(blockchainService, times(1)).getBalance("0xABC");

        service.invalidateUserBalances(1L);
        verify(balanceService).deleteAllForWalletIds(List.of(10L));

        assertThat(service.getBalance(1L, 10L, "0xABC", BlockchainNetwork.ETH)).isEqualTo("7.0");
        verify(blockchainService, times(2)).getBalance("0xABC");
    }

    @Test
    void creditBalance_evictsCacheInsteadOfAdding() {
        when(blockchainService.getBalance("0xABC")).thenReturn("100.0", "150.0");
        when(balanceService.findFreshBalance(anyLong(), any(), anyLong())).thenReturn(Optional.empty());

        assertThat(service.getBalance(1L, 10L, "0xABC", BlockchainNetwork.USDT_TRC20)).isEqualTo("100.0");

        service.creditBalance(1L, 10L, BlockchainNetwork.USDT_TRC20, new BigDecimal("50"));

        verify(balanceService).deleteByWalletIdAndNetwork(10L, BlockchainNetwork.USDT_TRC20);
        assertThat(service.getBalance(1L, 10L, "0xABC", BlockchainNetwork.USDT_TRC20)).isEqualTo("150.0");
        verify(blockchainService, times(2)).getBalance("0xABC");
    }

    @Test
    void creditBalance_delayedCredit_doesNotDoubleAnAlreadyRefreshedBalance() {
        when(blockchainService.getBalance("0xABC")).thenReturn("150.0");
        when(balanceService.findFreshBalance(anyLong(), any(), anyLong())).thenReturn(Optional.empty());
        assertThat(service.getBalance(1L, 10L, "0xABC", BlockchainNetwork.USDT_TRC20)).isEqualTo("150.0");
        clearInvocations(balanceService);

        service.creditBalance(1L, 10L, BlockchainNetwork.USDT_TRC20, new BigDecimal("50"));

        verify(balanceService, never()).upsertBalance(anyLong(), any(), any());
    }

    @Test
    void creditBalance_invalidArgs_noOp() {
        service.creditBalance(null, 10L, BlockchainNetwork.ETH, BigDecimal.ONE);
        service.creditBalance(1L, 10L, BlockchainNetwork.ETH, BigDecimal.ZERO);
        service.creditBalance(1L, null, BlockchainNetwork.ETH, BigDecimal.ONE);

        verify(balanceService, never()).deleteByWalletIdAndNetwork(anyLong(), any());
    }
}
