package com.example.walletbot.wallet.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface WalletNetworkBalanceRepository extends JpaRepository<WalletNetworkBalanceEntity, Long> {

    Optional<WalletNetworkBalanceEntity> findByWalletIdAndNetwork(Long walletId, BlockchainNetwork network);

    @Modifying
    @Query("DELETE FROM WalletNetworkBalanceEntity w WHERE w.walletId IN :walletIds")
    void deleteAllByWalletIdIn(@Param("walletIds") Collection<Long> walletIds);

    @Modifying
    @Query("DELETE FROM WalletNetworkBalanceEntity w WHERE w.walletId = :walletId AND w.network = :network")
    void deleteByWalletIdAndNetwork(@Param("walletId") long walletId, @Param("network") BlockchainNetwork network);

    @Modifying
    @Query(value = """
            INSERT INTO wallet_network_balances (wallet_id, network, balance, updated_at)
            VALUES (:walletId, :network, :balance, :updatedAt)
            ON CONFLICT (wallet_id, network)
            DO UPDATE SET balance = EXCLUDED.balance, updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsert(@Param("walletId") long walletId,
                @Param("network") String network,
                @Param("balance") String balance,
                @Param("updatedAt") Instant updatedAt);
}
