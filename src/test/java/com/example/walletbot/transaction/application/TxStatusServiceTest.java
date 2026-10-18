package com.example.walletbot.transaction.application;

import com.example.walletbot.asset.application.AssetService;
import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.notification.application.NotificationService;
import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainService.TxStatusResult;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.platform.monitoring.MonitoringService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.constant.NotificationType;
import com.example.walletbot.transaction.domain.TransactionLogEntity;
import com.example.walletbot.transaction.domain.TransactionLogRepository;
import com.example.walletbot.transaction.infrastructure.TransactionSchedulerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TxStatusServiceTest {

    @Mock
    TransactionLogRepository transactionLogRepository;
    @Mock
    TransactionLogService transactionLogService;
    @Mock
    BlockchainServiceFactory blockchainFactory;
    @Mock
    PriceService priceService;
    @Mock
    MonitoringService monitoringService;
    @Mock
    NotificationService notificationService;
    @Mock
    BlockchainService blockchainService;
    @Mock
    AssetService assetService;
    @InjectMocks
    TxStatusService service;
    private TransactionSchedulerProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TransactionSchedulerProperties();
        service = new TxStatusService(transactionLogRepository, transactionLogService,
                blockchainFactory, priceService, monitoringService, notificationService, properties,
                assetService);
        when(blockchainFactory.getService(any())).thenReturn(blockchainService);
        when(priceService.getUsdPriceBySymbol(any())).thenReturn(Optional.of(new BigDecimal("2000")));
    }

    @Test
    void pollAndApply_confirmed_marksSuccessAndNotifies() {
        TransactionLogEntity tx = pendingTx(BlockchainNetwork.ETH);
        when(blockchainService.getTxStatus("0xabc", "0xfrom"))
                .thenReturn(TxStatusResult.confirmed(LocalDateTime.now(ZoneOffset.UTC), new BigDecimal("0.001")));

        service.pollAndApply(tx);

        verify(transactionLogService).markSuccess(eq(tx), eq("0xabc"), any(), any());
        verify(monitoringService).incrementTransactionSent();
        verify(assetService).invalidateUserBalances(tx.getUserId());
        verify(notificationService).send(eq(tx.getUserId()), eq(NotificationType.SEND_SUCCESS),
                any(), any(), any(), any(), any());
    }

    @Test
    void pollAndApply_reverted_marksFailedAndNotifies() {
        TransactionLogEntity tx = pendingTx(BlockchainNetwork.ETH);
        when(blockchainService.getTxStatus("0xabc", "0xfrom"))
                .thenReturn(TxStatusResult.reverted(new BigDecimal("0.0005")));

        service.pollAndApply(tx);

        verify(transactionLogService).markFailed(tx, "Reverted on-chain");
        verify(assetService).invalidateUserBalances(tx.getUserId());
        verify(monitoringService, never()).incrementTransactionSent();
        verify(notificationService).send(eq(tx.getUserId()), eq(NotificationType.SEND_FAILED),
                any(), any(), any(), any(), any());
    }

    @Test
    void pollAndApply_pendingWithinTimeout_doesNothing() {
        TransactionLogEntity tx = pendingTx(BlockchainNetwork.ETH);
        tx.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        when(blockchainService.getTxStatus("0xabc", "0xfrom")).thenReturn(TxStatusResult.pending());

        service.pollAndApply(tx);

        verify(transactionLogService, never()).markSuccess(any(), any(), any(), any());
        verify(transactionLogService, never()).markFailed(any(), any());
        verify(transactionLogService, never()).markFailedTimeout(any());
    }

    @Test
    void pollAndApply_pendingPastTimeout_marksFailedTimeout() {
        TransactionLogEntity tx = pendingTx(BlockchainNetwork.SOL);
        tx.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minus(Duration.ofHours(2)));
        when(blockchainService.getTxStatus("0xabc", "0xfrom")).thenReturn(TxStatusResult.pending());

        service.pollAndApply(tx);

        verify(transactionLogService).markFailedTimeout(tx);
        verify(notificationService).send(eq(tx.getUserId()), eq(NotificationType.SEND_FAILED),
                any(), any(), any(), any(), any());
    }

    @Test
    void pollAndApply_rpcThrows_keepsPending() {
        TransactionLogEntity tx = pendingTx(BlockchainNetwork.ETH);
        tx.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        when(blockchainService.getTxStatus(any(), any())).thenThrow(new RuntimeException("RPC down"));

        service.pollAndApply(tx);

        verify(transactionLogService, never()).markSuccess(any(), any(), any(), any());
        verify(transactionLogService, never()).markFailed(any(), any());
        verify(transactionLogService, never()).markFailedTimeout(any());
    }

    private TransactionLogEntity pendingTx(BlockchainNetwork network) {
        TransactionLogEntity tx = new TransactionLogEntity();
        tx.setId(42L);
        tx.setUserId(7L);
        tx.setWalletId(11L);
        tx.setType(TransactionLogEntity.TxType.SEND);
        tx.setNetwork(network);
        tx.setSymbol(network.getSymbol());
        tx.setAmount(new BigDecimal("1.0"));
        tx.setFromAddress("0xfrom");
        tx.setToAddress("0xto");
        tx.setTxHash("0xabc");
        tx.setStatus(TransactionLogEntity.TxStatus.PENDING);
        tx.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return tx;
    }
}
