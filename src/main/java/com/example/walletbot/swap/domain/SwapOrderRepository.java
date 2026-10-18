package com.example.walletbot.swap.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SwapOrderRepository extends JpaRepository<SwapOrderEntity, Long> {

    Optional<SwapOrderEntity> findByUserIdAndIdemKey(Long userId, String idemKey);

    Optional<SwapOrderEntity> findByIntentJti(String intentJti);

    List<SwapOrderEntity> findByStatusIn(List<SwapOrderStatus> statuses);

    List<SwapOrderEntity> findByStatusInAndExpiresAtBefore(List<SwapOrderStatus> statuses, Instant cutoff);

    List<SwapOrderEntity> findByStatusInAndUpdatedAtBefore(List<SwapOrderStatus> statuses, Instant cutoff);

    boolean existsByToWalletIdAndDestTxHash(Long toWalletId, String destTxHash);

    boolean existsByToWalletIdAndToNetworkAndStatusIn(
            Long toWalletId, BlockchainNetwork toNetwork, Collection<SwapOrderStatus> statuses);

    @Query("SELECT o.fromNetwork, COUNT(o), COALESCE(SUM(o.amountIn), 0) "
            + "FROM SwapOrderEntity o "
            + "WHERE o.status = :status AND o.createdAt >= :from AND o.createdAt <= :to "
            + "GROUP BY o.fromNetwork")
    List<Object[]> aggregateCompletedSwaps(@Param("status") SwapOrderStatus status,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to);

    @Query("SELECT o FROM SwapOrderEntity o WHERE o.userId = :userId "
            + "AND o.status NOT IN :hidden "
            + "AND NOT (o.status IN :hiddenWhenNoOnchainAction "
            + "         AND o.sourceTxHash IS NULL) "
            + "ORDER BY o.createdAt DESC")
    List<SwapOrderEntity> findDisplayableByUserId(
            @Param("userId") Long userId,
            @Param("hidden") Collection<SwapOrderStatus> hidden,
            @Param("hiddenWhenNoOnchainAction") Collection<SwapOrderStatus> hiddenWhenNoOnchainAction);
}
