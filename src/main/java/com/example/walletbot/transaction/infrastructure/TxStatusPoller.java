package com.example.walletbot.transaction.infrastructure;

import com.example.walletbot.transaction.application.TxStatusService;
import com.example.walletbot.transaction.domain.TransactionLogEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "transaction.scheduler.status-poller",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class TxStatusPoller {

    private final TxStatusService txStatusService;

    @Scheduled(fixedDelayString = "${transaction.scheduler.status-poller.delay:PT15S}",
            initialDelayString = "${transaction.scheduler.status-poller.initial-delay:PT10S}")
    public void poll() {
        List<TransactionLogEntity> pending = txStatusService.findPendingForPolling();
        if (pending.isEmpty()) return;
        log.debug("TxStatusPoller: {} pending tx to inspect", pending.size());
        for (TransactionLogEntity tx : pending) {
            try {
                txStatusService.pollAndApply(tx);
            } catch (Exception e) {
                log.warn("Status poll failed for tx {}: {}", tx.getId(), e.getMessage());
            }
        }
    }
}
