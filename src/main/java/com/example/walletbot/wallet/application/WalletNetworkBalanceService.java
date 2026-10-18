package com.example.walletbot.wallet.application;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.wallet.domain.WalletNetworkBalanceEntity;
import com.example.walletbot.wallet.domain.WalletNetworkBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletNetworkBalanceService {

    private final WalletNetworkBalanceRepository repository;

    public Optional<String> findFreshBalance(long walletId, BlockchainNetwork network, long maxAgeMs) {
        if (maxAgeMs <= 0) {
            return Optional.empty();
        }
        return repository.findByWalletIdAndNetwork(walletId, network)
                .filter(e -> !e.getUpdatedAt().isBefore(Instant.now().minusMillis(maxAgeMs)))
                .map(WalletNetworkBalanceEntity::getBalance);
    }

    public Optional<String> findLastKnownBalance(long walletId, BlockchainNetwork network) {
        return repository.findByWalletIdAndNetwork(walletId, network)
                .map(WalletNetworkBalanceEntity::getBalance);
    }

    @Transactional
    public void upsertBalance(long walletId, BlockchainNetwork network, String balance) {
        repository.upsert(walletId, network.name(), balance, Instant.now());
    }

    @Transactional
    public void deleteAllForWalletIds(Collection<Long> walletIds) {
        if (walletIds == null || walletIds.isEmpty()) {
            return;
        }
        repository.deleteAllByWalletIdIn(walletIds);
    }

    @Transactional
    public void deleteByWalletIdAndNetwork(long walletId, BlockchainNetwork network) {
        repository.deleteByWalletIdAndNetwork(walletId, network);
    }
}
