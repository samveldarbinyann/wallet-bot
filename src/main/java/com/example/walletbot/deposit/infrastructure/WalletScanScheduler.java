package com.example.walletbot.deposit.infrastructure;

import com.example.walletbot.deposit.application.DepositScanService;
import com.example.walletbot.transaction.application.TransactionLogService;
import com.example.walletbot.user.application.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletScanScheduler {

    private static final Duration ACTIVITY_WINDOW = Duration.ofHours(48);

    private final UserService userService;
    private final DepositScanService depositScanService;
    private final TransactionLogService transactionLogService;

    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 60 * 1000)
    public void scanActiveUsers() {
        List<Long> userIds = userService.findActiveUserIds(Instant.now().minus(ACTIVITY_WINDOW));
        log.info("WalletScanScheduler: scanning {} active users", userIds.size());
        scanBatch(userIds);
    }

    @Scheduled(fixedDelay = 2 * 60 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    public void scanInactiveUsers() {
        Set<Long> funded = new HashSet<>(transactionLogService.findUserIdsWithDeposits());
        if (funded.isEmpty()) {
            return;
        }
        List<Long> userIds = userService.findInactiveUserIds(Instant.now().minus(ACTIVITY_WINDOW));
        userIds.retainAll(funded);
        log.info("WalletScanScheduler: scanning {} inactive funded users", userIds.size());
        scanBatch(userIds);
    }

    private void scanBatch(List<Long> userIds) {
        for (Long userId : userIds) {
            try {
                depositScanService.scanUserWallets(userId);
            } catch (Exception e) {
                log.warn("WalletScanScheduler: error scanning user {}: {}", userId, e.getMessage());
            }
        }
    }
}