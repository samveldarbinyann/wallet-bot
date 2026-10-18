package com.example.walletbot.transaction.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface SendIdempotencyRepository extends JpaRepository<SendIdempotencyEntity, Long> {

    Optional<SendIdempotencyEntity> findByUserIdAndIdemKey(Long userId, String idemKey);

    @Modifying
    @Query("UPDATE SendIdempotencyEntity s SET s.txHash = :txHash, s.completedAt = :completedAt WHERE s.id = :id")
    int markCompleted(@Param("id") Long id, @Param("txHash") String txHash, @Param("completedAt") Instant completedAt);

    @Modifying
    @Query("DELETE FROM SendIdempotencyEntity s WHERE s.id = :id AND s.txHash IS NULL")
    int deleteByIdIfPending(@Param("id") Long id);
}
