package com.example.walletbot.deposit.application;

import com.example.walletbot.deposit.application.scanner.BtcDepositScanner;
import com.example.walletbot.deposit.application.scanner.TonDepositScanner;
import com.example.walletbot.deposit.application.scanner.TronDepositScanner;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.domain.WalletRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepositScanService {

    private static final int SLOW_CHAIN_DEBOUNCE_MINUTES = 12;
    private static final int FAST_CHAIN_DEBOUNCE_MINUTES = 2;

    private final WalletRepository walletRepository;
    private final BtcDepositScanner btcDepositScanner;
    private final TronDepositScanner tronDepositScanner;
    private final TonDepositScanner tonDepositScanner;
    private final Cache<Long, Object> userScanLocks = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(50_000)
            .build();

    @Setter(onMethod_ = {@Autowired, @Qualifier("ioExecutor")})
    private Executor ioExecutor;

    public void scanUserWalletsAsync(long userId) {
        CompletableFuture.runAsync(() -> scanUserWallets(userId), ioExecutor)
                .exceptionally(ex -> null);
    }

    public void scanUserWallets(Long userId) {
        if (userId == null)
            return;
        Object lock = userScanLocks.get(userId, k -> new Object());
        synchronized (lock) {
            List<WalletEntity> wallets = walletRepository.findByUserEntityId(userId);
            if (wallets.isEmpty())
                return;
            log.info("Scanning {} wallets for user {}", wallets.size(), userId);
            for (WalletEntity wallet : wallets) {
                try {
                    scanSingleWallet(wallet);
                } catch (Exception e) {
                    log.warn("Scan error wallet {}: {}", wallet.getId(), e.getMessage());
                }
            }
        }
    }

    private static int debounceMinutes(BlockchainNetwork network) {
        return switch (network) {
            case BTC -> SLOW_CHAIN_DEBOUNCE_MINUTES;
            default -> FAST_CHAIN_DEBOUNCE_MINUTES;
        };
    }

    public void scanSingleWallet(WalletEntity wallet) {
        BlockchainNetwork network = wallet.getNetwork();
        if (network == null || wallet.getUserEntity() == null)
            return;

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime lastScan = wallet.getLastDepositScanAt();
        if (lastScan != null && lastScan.isAfter(now.minusMinutes(debounceMinutes(network)))) {
            return;
        }

        boolean ok = switch (network) {
            case BTC -> btcDepositScanner.scan(wallet);
            case TRX -> tronDepositScanner.scan(wallet);
            case TON -> tonDepositScanner.scan(wallet);
            default -> false;
        };

        if (ok) {
            wallet.setLastDepositScanAt(now);
            walletRepository.save(wallet);
        }
    }
}