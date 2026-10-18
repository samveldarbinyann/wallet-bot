package com.example.walletbot.asset.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

public interface OutgoingReservationPort {

    BigDecimal reservedOutgoing(Long walletId, BlockchainNetwork network);

    Map<Long, Map<BlockchainNetwork, BigDecimal>> reservedOutgoingBatch(Collection<Long> walletIds);

    BigDecimal pendingBaselineConfirmed(Long walletId, BlockchainNetwork network);

    BigDecimal effectiveConfirmed(Long walletId, BlockchainNetwork network, BigDecimal confirmedBalance, BigDecimal reserved);

    BigDecimal effectiveAvailable(Long walletId, BlockchainNetwork network, BigDecimal confirmedBalance);

    void invalidateBaseline(Long walletId, BlockchainNetwork network);
}