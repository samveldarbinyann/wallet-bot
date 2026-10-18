package com.example.walletbot.transaction.application;

import com.example.walletbot.asset.application.AssetService;
import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.notification.application.NotificationService;
import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainService.OnChainStatus;
import com.example.walletbot.platform.blockchain.BlockchainService.TxStatusResult;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.platform.monitoring.MonitoringService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.constant.NotificationType;
import com.example.walletbot.shared.util.AmountFormatter;
import com.example.walletbot.shared.util.BlockchainExplorerUtil;
import com.example.walletbot.transaction.domain.TransactionLogEntity;
import com.example.walletbot.transaction.domain.TransactionLogRepository;
import com.example.walletbot.transaction.infrastructure.TransactionSchedulerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TxStatusService {

    private final TransactionLogRepository transactionLogRepository;
    private final TransactionLogService transactionLogService;
    private final BlockchainServiceFactory blockchainFactory;
    private final PriceService priceService;
    private final MonitoringService monitoringService;
    private final NotificationService notificationService;
    private final TransactionSchedulerProperties properties;
    private final AssetService assetService;

    public List<TransactionLogEntity> findPendingForPolling() {
        return transactionLogRepository.findPendingForPolling(
                PageRequest.of(0, properties.getBatchSize()));
    }

    public void pollAndApply(TransactionLogEntity tx) {
        BlockchainNetwork network = tx.getNetwork();
        BlockchainService service;
        try {
            service = blockchainFactory.getService(network);
        } catch (Exception e) {
            log.warn("No BlockchainService for {} — skipping tx {}", network, tx.getId());
            return;
        }

        TxStatusResult result;
        try {
            result = service.getTxStatus(tx.getTxHash(), tx.getFromAddress());
        } catch (UnsupportedOperationException e) {
            log.warn("getTxStatus not implemented for {} — skipping tx {}", network, tx.getId());
            return;
        } catch (Exception e) {
            log.warn("getTxStatus threw for {} tx {}: {}", network, tx.getId(), e.getMessage());
            result = TxStatusResult.pending();
        }

        OnChainStatus status = result.status();
        switch (status) {
            case CONFIRMED -> applyConfirmed(tx, result);
            case REVERTED -> applyReverted(tx, result);
            case PENDING, NOT_FOUND -> applyPending(tx);
        }
    }

    private void applyConfirmed(TransactionLogEntity tx, TxStatusResult result) {
        BigDecimal feeUsd = computeFeeUsd(tx.getNetwork(), result.feeNative());
        transactionLogService.markSuccess(tx, tx.getTxHash(), result.blockTimestamp(), feeUsd);
        assetService.invalidateUserBalances(tx.getUserId());
        monitoringService.incrementTransactionSent();

        String explorerUrl = BlockchainExplorerUtil.getTxUrl(tx.getNetwork(), tx.getTxHash());
        notificationService.send(tx.getUserId(), NotificationType.SEND_SUCCESS,
                Map.of("txId", tx.getId(), "explorerUrl", explorerUrl != null ? explorerUrl : ""),
                "notification.send.title", "notification.send.body",
                AmountFormatter.forDisplay(tx.getAmount()),
                tx.getSymbol() != null ? tx.getSymbol() : "");
        log.info("Tx {} confirmed on-chain ({} {})", tx.getId(), tx.getNetwork(), tx.getTxHash());
    }

    private void applyReverted(TransactionLogEntity tx, TxStatusResult result) {
        BigDecimal feeUsd = computeFeeUsd(tx.getNetwork(), result.feeNative());
        if (feeUsd != null) {
            tx.setFeeUsd(feeUsd.setScale(2, RoundingMode.HALF_UP));
        }
        transactionLogService.markFailed(tx, "Reverted on-chain");
        assetService.invalidateUserBalances(tx.getUserId());
        notifySendFailed(tx);
        log.info("Tx {} reverted on-chain ({} {})", tx.getId(), tx.getNetwork(), tx.getTxHash());
    }

    private void applyPending(TransactionLogEntity tx) {
        LocalDateTime createdAt = tx.getCreatedAt();
        if (createdAt == null) return;
        Duration age = Duration.between(createdAt, LocalDateTime.now(ZoneOffset.UTC));
        Duration timeout = properties.timeoutFor(tx.getNetwork());
        if (age.compareTo(timeout) > 0) {
            transactionLogService.markFailedTimeout(tx);
            notifySendFailed(tx);
            log.warn("Tx {} marked FAILED after {} timeout ({} {})",
                    tx.getId(), timeout, tx.getNetwork(), tx.getTxHash());
        }
    }

    private void notifySendFailed(TransactionLogEntity tx) {
        notificationService.send(tx.getUserId(), NotificationType.SEND_FAILED,
                Map.of("txId", tx.getId()),
                "notification.send.failed.title", "notification.send.failed.body",
                AmountFormatter.forDisplay(tx.getAmount()),
                tx.getSymbol() != null ? tx.getSymbol() : "");
    }

    private BigDecimal computeFeeUsd(BlockchainNetwork network, BigDecimal feeNative) {
        if (feeNative == null || feeNative.signum() <= 0) return null;
        BlockchainNetwork gasNetwork = network.getGasNetwork() != null
                ? network.getGasNetwork() : network;
        return priceService.getUsdPriceBySymbol(gasNetwork.getSymbol())
                .map(price -> price.multiply(feeNative))
                .orElse(null);
    }
}
