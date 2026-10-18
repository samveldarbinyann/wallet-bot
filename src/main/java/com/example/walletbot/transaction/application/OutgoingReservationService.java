package com.example.walletbot.transaction.application;

import com.example.walletbot.asset.domain.OutgoingReservationPort;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.transaction.domain.TransactionLogRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OutgoingReservationService implements OutgoingReservationPort {

    private final TransactionLogRepository transactionLogRepository;
    private final Cache<String, BigDecimal> broadcastBaseline = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(50_000)
            .build();

    private static String key(Long walletId, BlockchainNetwork network) {
        return walletId + ":" + network.name();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal reservedOutgoing(Long walletId, BlockchainNetwork network) {
        if (walletId == null || network == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal reserved = transactionLogRepository.sumReservedOutgoing(walletId, network);
        return reserved != null ? reserved : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Map<BlockchainNetwork, BigDecimal>> reservedOutgoingBatch(Collection<Long> walletIds) {
        if (walletIds == null || walletIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<BlockchainNetwork, BigDecimal>> result = new HashMap<>();
        for (Object[] row : transactionLogRepository.sumReservedOutgoingByWallets(walletIds)) {
            Long walletId = (Long) row[0];
            BlockchainNetwork network = (BlockchainNetwork) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            result.computeIfAbsent(walletId, k -> new HashMap<>()).put(network, amount);
        }
        return result;
    }

    @Override
    public BigDecimal pendingBaselineConfirmed(Long walletId, BlockchainNetwork network) {
        if (walletId == null || network == null) {
            return null;
        }
        return broadcastBaseline.getIfPresent(key(walletId, network));
    }

    public void recordBroadcastBaseline(Long walletId, BlockchainNetwork network, BigDecimal confirmed) {
        if (walletId == null || network == null || confirmed == null) {
            return;
        }
        broadcastBaseline.asMap().putIfAbsent(key(walletId, network), confirmed);
    }

    @Override
    public void invalidateBaseline(Long walletId, BlockchainNetwork network) {
        if (walletId == null || network == null) {
            return;
        }
        broadcastBaseline.invalidate(key(walletId, network));
    }

    @Override
    public BigDecimal effectiveConfirmed(Long walletId, BlockchainNetwork network, BigDecimal confirmedBalance, BigDecimal reserved) {
        if (confirmedBalance == null) {
            return BigDecimal.ZERO;
        }
        if (reserved == null || reserved.signum() <= 0) {
            return confirmedBalance.max(BigDecimal.ZERO);
        }
        BigDecimal baseline = pendingBaselineConfirmed(walletId, network);
        return baseline != null ? confirmedBalance.max(baseline) : confirmedBalance;
    }

    @Override
    public BigDecimal effectiveAvailable(Long walletId, BlockchainNetwork network, BigDecimal confirmedBalance) {
        if (confirmedBalance == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal reserved = reservedOutgoing(walletId, network);
        if (reserved.signum() <= 0) {
            invalidateBaseline(walletId, network);
            return confirmedBalance.max(BigDecimal.ZERO);
        }
        return effectiveConfirmed(walletId, network, confirmedBalance, reserved).subtract(reserved).max(BigDecimal.ZERO);
    }
}
