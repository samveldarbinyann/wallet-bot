package com.example.walletbot.transaction.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TransactionLogRepository extends JpaRepository<TransactionLogEntity, Long> {

    @Query("SELECT t FROM TransactionLogEntity t WHERE t.userId = :userId ORDER BY COALESCE(t.txTimestamp, t.createdAt) DESC")
    Page<TransactionLogEntity> findByUserIdOrderByDisplayTimeDesc(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT t FROM TransactionLogEntity t WHERE t.userId = :userId "
            + "AND (t.txHash IS NULL OR t.txHash NOT IN :hashes) "
            + "ORDER BY COALESCE(t.txTimestamp, t.createdAt) DESC")
    List<TransactionLogEntity> findRecentExcludingTxHashes(@Param("userId") Long userId,
                                                           @Param("hashes") Collection<String> hashes,
                                                           Pageable pageable);

    @Query("SELECT COUNT(t) FROM TransactionLogEntity t WHERE t.userId = :userId "
            + "AND (t.txHash IS NULL OR t.txHash NOT IN :hashes)")
    long countExcludingTxHashes(@Param("userId") Long userId,
                                @Param("hashes") Collection<String> hashes);

    @Query(value = """
            SELECT
                t.type,
                t.symbol,
                COUNT(*) AS cnt,
                COALESCE(SUM(CASE WHEN t.usd_value IS NOT NULL AND t.usd_value > 0 THEN t.usd_value ELSE 0 END), 0) AS stored_usd,
                COALESCE(SUM(CASE WHEN t.usd_value IS NULL OR t.usd_value = 0 THEN t.amount ELSE 0 END), 0) AS fallback_amount
            FROM transaction_logs t
            WHERE COALESCE(t.tx_timestamp, t.created_at) >= :from
              AND COALESCE(t.tx_timestamp, t.created_at) <= :to
              AND t.status = 'SUCCESS'
            GROUP BY t.type, t.symbol
            """, nativeQuery = true)
    List<Object[]> aggregateTurnoverTotals(@Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    boolean existsByWalletIdAndTxHash(Long walletId, String txHash);

    @Query("SELECT t.txHash FROM TransactionLogEntity t WHERE t.walletId = :walletId AND t.txHash IN :txHashes")
    Set<String> findExistingTxHashes(@Param("walletId") Long walletId, @Param("txHashes") Collection<String> txHashes);

    Optional<TransactionLogEntity> findByWalletIdAndTxHash(Long walletId, String txHash);

    @Query("SELECT t FROM TransactionLogEntity t WHERE t.status = 'PENDING' "
            + "AND t.type = 'SEND' AND t.txHash IS NOT NULL "
            + "ORDER BY t.createdAt ASC")
    List<TransactionLogEntity> findPendingForPolling(Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionLogEntity t "
            + "WHERE t.walletId = :walletId AND t.network = :network "
            + "AND t.type = 'SEND' AND t.status = 'PENDING'")
    BigDecimal sumReservedOutgoing(@Param("walletId") Long walletId,
                                   @Param("network") BlockchainNetwork network);

    @Query("SELECT t.walletId, t.network, COALESCE(SUM(t.amount), 0) FROM TransactionLogEntity t "
            + "WHERE t.walletId IN :walletIds AND t.type = 'SEND' AND t.status = 'PENDING' "
            + "GROUP BY t.walletId, t.network")
    List<Object[]> sumReservedOutgoingByWallets(@Param("walletIds") Collection<Long> walletIds);

    @Query("SELECT DISTINCT t.userId FROM TransactionLogEntity t "
            + "WHERE t.type = 'DEPOSIT' AND t.userId IS NOT NULL")
    List<Long> findUserIdsWithDeposits();
}
