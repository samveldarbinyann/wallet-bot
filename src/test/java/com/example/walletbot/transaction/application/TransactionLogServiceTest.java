package com.example.walletbot.transaction.application;

import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.notification.application.NotificationService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.constant.NotificationType;
import com.example.walletbot.shared.event.WalletDepositEvent;
import com.example.walletbot.swap.application.SwapStatusService;
import com.example.walletbot.transaction.domain.TransactionLogEntity;
import com.example.walletbot.transaction.domain.TransactionLogEntity.TxStatus;
import com.example.walletbot.transaction.domain.TransactionLogEntity.TxType;
import com.example.walletbot.transaction.domain.TransactionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionLogServiceTest {

    @Mock
    TransactionLogRepository repo;
    @Mock
    PriceService priceService;
    @Mock
    NotificationService notificationService;
    @Mock
    SwapStatusService swapStatusService;
    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    TransactionLogService service;

    @BeforeEach
    void setUp() {
        when(repo.save(any())).thenAnswer(inv -> {
            TransactionLogEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(100L);
            return e;
        });
    }

    private TransactionLogEntity sendTx() {
        TransactionLogEntity tx = new TransactionLogEntity();
        tx.setType(TxType.SEND);
        tx.setSymbol("ETH");
        tx.setAmount(new BigDecimal("2"));
        return tx;
    }

    @Test
    void markSuccess_setsStatusAndComputesUsdValue() {
        when(priceService.getUsdPriceBySymbol("ETH")).thenReturn(Optional.of(new BigDecimal("3000")));
        TransactionLogEntity tx = sendTx();

        service.markSuccess(tx, "0xhash", LocalDateTime.now(), new BigDecimal("1.234"));

        assertThat(tx.getStatus()).isEqualTo(TxStatus.SUCCESS);
        assertThat(tx.getTxHash()).isEqualTo("0xhash");
        assertThat(tx.getErrorMessage()).isNull();
        assertThat(tx.getUsdValue()).isEqualByComparingTo("6000.00");
        assertThat(tx.getFeeUsd()).isEqualByComparingTo("1.23");
    }

    @Test
    void markFailed_setsFailedStatusAndMessage() {
        TransactionLogEntity tx = sendTx();
        service.markFailed(tx, "insufficient funds");

        assertThat(tx.getStatus()).isEqualTo(TxStatus.FAILED);
        assertThat(tx.getErrorMessage()).isEqualTo("insufficient funds");
    }

    @Test
    void markFailedTimeout_setsTimeoutMessage() {
        TransactionLogEntity tx = sendTx();
        service.markFailedTimeout(tx);

        assertThat(tx.getStatus()).isEqualTo(TxStatus.FAILED);
        assertThat(tx.getErrorMessage()).contains("timeout");
    }

    @Test
    void createPendingWithHash_savesPending() {
        TransactionLogEntity saved = service.createPendingWithHash(
                1L, 2L, TxType.SEND, BlockchainNetwork.ETH, "ETH",
                new BigDecimal("1"), "0xfrom", "0xto", "0xhash", null);

        assertThat(saved.getStatus()).isEqualTo(TxStatus.PENDING);
        assertThat(saved.getTxHash()).isEqualTo("0xhash");
    }

    @Test
    void createPendingWithHash_duplicate_returnsExistingRow() {
        doThrow(new DataIntegrityViolationException("dup")).when(repo).save(any());
        TransactionLogEntity existing = sendTx();
        when(repo.findByWalletIdAndTxHash(2L, "0xhash")).thenReturn(Optional.of(existing));

        TransactionLogEntity result = service.createPendingWithHash(
                1L, 2L, TxType.SEND, BlockchainNetwork.ETH, "ETH",
                new BigDecimal("1"), "0xfrom", "0xto", "0xhash", null);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void saveDepositSuccess_notSwapPayout_savesRowSendsNotificationAndPublishesEvent() {
        when(swapStatusService.isSwapPayout(anyLong(), anyString())).thenReturn(false);
        when(priceService.getUsdPriceBySymbol("ETH")).thenReturn(Optional.of(new BigDecimal("3000")));

        TransactionLogEntity saved = service.saveDepositSuccess(1L, 2L, BlockchainNetwork.ETH, "ETH",
                new BigDecimal("0.5"), "0xfrom", "0xto", "0xhash", LocalDateTime.now());

        assertThat(saved).isNotNull();
        verify(repo).save(any());
        verify(notificationService).send(eq(1L), eq(NotificationType.DEPOSIT), any(), any(), any(), any(), any());
        verify(eventPublisher).publishEvent(
                new WalletDepositEvent(1L, 2L, BlockchainNetwork.ETH, new BigDecimal("0.5"), "0xhash"));
    }

    @Test
    void saveDepositSuccess_swapPayoutAwaiting_savesRowButSuppressesDepositPush() {
        when(swapStatusService.isSwapPayout(anyLong(), anyString())).thenReturn(false);
        when(swapStatusService.hasPayoutAwaiting(2L, BlockchainNetwork.USDT_ERC20)).thenReturn(true);
        when(priceService.getUsdPriceBySymbol("USDT")).thenReturn(Optional.of(BigDecimal.ONE));

        TransactionLogEntity saved = service.saveDepositSuccess(1L, 2L, BlockchainNetwork.USDT_ERC20, "USDT",
                new BigDecimal("10"), "0xfrom", "0xto", "0xhash", LocalDateTime.now());

        assertThat(saved).isNotNull();
        verify(repo).save(any());
        verify(notificationService, never()).send(any(), any(), any(), any(), any(), any(), any());
        verify(eventPublisher).publishEvent(any(WalletDepositEvent.class));
    }

    @Test
    void saveDepositSuccess_swapPayout_persistsNothingAndCreditsNothing() {
        when(swapStatusService.isSwapPayout(anyLong(), anyString())).thenReturn(true);

        TransactionLogEntity saved = service.saveDepositSuccess(1L, 2L, BlockchainNetwork.ETH, "ETH",
                new BigDecimal("0.5"), "0xfrom", "0xto", "0xhash", LocalDateTime.now());

        assertThat(saved).isNull();
        verify(repo, never()).save(any());
        verify(notificationService, never()).send(any(), any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void existsByWalletIdAndTxHash_delegates() {
        when(repo.existsByWalletIdAndTxHash(2L, "0xhash")).thenReturn(true);
        assertThat(service.existsByWalletIdAndTxHash(2L, "0xhash")).isTrue();
    }
}
