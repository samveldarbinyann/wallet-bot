package com.example.walletbot.transaction.application;

import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.notification.application.NotificationService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.constant.NotificationType;
import com.example.walletbot.shared.event.WalletDepositEvent;
import com.example.walletbot.shared.util.AmountFormatter;
import com.example.walletbot.swap.application.SwapStatusService;
import com.example.walletbot.transaction.domain.TransactionLogEntity;
import com.example.walletbot.transaction.domain.TransactionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionLogService {

    private final TransactionLogRepository transactionLogRepository;
    private final PriceService priceService;
    private final NotificationService notificationService;
    private final SwapStatusService swapStatusService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TransactionLogEntity createPendingWithHash(
            Long userId,
            Long walletId,
            TransactionLogEntity.TxType type,
            BlockchainNetwork network,
            String symbol,
            BigDecimal amount,
            String fromAddress,
            String toAddress,
            String txHash,
            String externalRef
    ) {
        TransactionLogEntity txLog = new TransactionLogEntity();
        txLog.setUserId(userId);
        txLog.setWalletId(walletId);
        txLog.setType(type);
        txLog.setNetwork(network);
        txLog.setSymbol(symbol);
        txLog.setAmount(amount);
        txLog.setFromAddress(fromAddress);
        txLog.setToAddress(toAddress);
        txLog.setTxHash(txHash);
        txLog.setExternalRef(externalRef);
        txLog.setStatus(TransactionLogEntity.TxStatus.PENDING);
        try {
            return transactionLogRepository.save(txLog);
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate pending row skipped: walletId={} txHash={}", walletId, txHash);
            return transactionLogRepository.findByWalletIdAndTxHash(walletId, txHash)
                    .orElse(txLog);
        }
    }

    @Transactional
    public void markFailedById(Long id, String errorMessage) {
        transactionLogRepository.findById(id).ifPresent(txLog -> markFailed(txLog, errorMessage));
    }

    @Transactional
    public TransactionLogEntity markSuccess(TransactionLogEntity txLog, String txHash,
                                            LocalDateTime txTimestamp, BigDecimal feeUsd) {
        txLog.setTxHash(txHash);
        txLog.setStatus(TransactionLogEntity.TxStatus.SUCCESS);
        txLog.setErrorMessage(null);
        if (txTimestamp != null) {
            txLog.setTxTimestamp(txTimestamp);
        }
        applyUsdValue(txLog, txLog.getAmount(), txLog.getSymbol());
        if (feeUsd != null) {
            txLog.setFeeUsd(feeUsd.setScale(2, RoundingMode.HALF_UP));
        }
        return transactionLogRepository.save(txLog);
    }

    @Transactional
    public TransactionLogEntity markFailed(TransactionLogEntity txLog, String errorMessage) {
        txLog.setStatus(TransactionLogEntity.TxStatus.FAILED);
        txLog.setErrorMessage(errorMessage);
        return transactionLogRepository.save(txLog);
    }

    @Transactional
    public TransactionLogEntity markFailedTimeout(TransactionLogEntity txLog) {
        return markFailed(txLog, "Transaction not confirmed on-chain within timeout window");
    }

    @Transactional
    public TransactionLogEntity saveDepositSuccess(
            Long userId,
            Long walletId,
            BlockchainNetwork network,
            String symbol,
            BigDecimal amount,
            String fromAddress,
            String toAddress,
            String txHash,
            LocalDateTime txTimestamp
    ) {
        if (swapStatusService.isSwapPayout(walletId, txHash)) {
            return null;
        }

        TransactionLogEntity txLog = new TransactionLogEntity();
        txLog.setUserId(userId);
        txLog.setWalletId(walletId);
        txLog.setType(TransactionLogEntity.TxType.DEPOSIT);
        txLog.setNetwork(network);
        txLog.setSymbol(symbol);
        txLog.setAmount(amount);
        txLog.setFromAddress(fromAddress);
        txLog.setToAddress(toAddress);
        txLog.setTxHash(txHash);
        txLog.setTxTimestamp(txTimestamp);
        txLog.setStatus(TransactionLogEntity.TxStatus.SUCCESS);
        applyUsdValue(txLog, amount, symbol);
        try {
            TransactionLogEntity saved = transactionLogRepository.save(txLog);
            if (amount != null && symbol != null
                    && !swapStatusService.hasPayoutAwaiting(walletId, network)) {
                notificationService.send(userId, NotificationType.DEPOSIT, Map.of("txId", saved.getId()),
                        "notification.deposit.title", "notification.deposit.body",
                        AmountFormatter.forDisplay(amount), symbol);
            }
            eventPublisher.publishEvent(
                    new WalletDepositEvent(userId, walletId, network, amount, txHash));
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate deposit skipped: walletId={} txHash={}", walletId, txHash);
            return transactionLogRepository.findByWalletIdAndTxHash(walletId, txHash)
                    .orElse(txLog);
        }
    }

    public boolean existsByWalletIdAndTxHash(Long walletId, String txHash) {
        return transactionLogRepository.existsByWalletIdAndTxHash(walletId, txHash);
    }

    public Set<String> findExistingTxHashes(Long walletId, Collection<String> txHashes) {
        if (txHashes == null || txHashes.isEmpty()) {
            return Set.of();
        }
        return transactionLogRepository.findExistingTxHashes(walletId, txHashes);
    }

    @Transactional(readOnly = true)
    public List<Long> findUserIdsWithDeposits() {
        return transactionLogRepository.findUserIdsWithDeposits();
    }

    public Long findIdByWalletAndHash(Long walletId, String txHash) {
        if (txHash == null) {
            return null;
        }
        return transactionLogRepository.findByWalletIdAndTxHash(walletId, txHash)
                .map(TransactionLogEntity::getId)
                .orElse(null);
    }

    private void applyUsdValue(TransactionLogEntity txLog, BigDecimal amount, String symbol) {
        if (amount == null || symbol == null) {
            return;
        }
        priceService.getUsdPriceBySymbol(symbol)
                .ifPresent(price -> txLog.setUsdValue(price.multiply(amount).setScale(2, RoundingMode.HALF_UP)));
    }
}
