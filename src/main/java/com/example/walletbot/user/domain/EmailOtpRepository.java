package com.example.walletbot.user.domain;

import com.example.walletbot.user.domain.EmailOtpEntity.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailOtpRepository extends JpaRepository<EmailOtpEntity, Long> {

    Optional<EmailOtpEntity> findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(
            Long userId, OtpPurpose purpose);

    @Modifying
    @Query("DELETE FROM EmailOtpEntity o WHERE o.user.id = :userId AND o.purpose = :purpose")
    void deleteByUserIdAndPurpose(@Param("userId") Long userId, @Param("purpose") OtpPurpose purpose);

    @Modifying
    @Query("DELETE FROM EmailOtpEntity o WHERE o.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM EmailOtpEntity o WHERE o.user.id = :userId AND o.purpose <> :purpose")
    void deleteByUserIdAndPurposeNot(@Param("userId") Long userId, @Param("purpose") OtpPurpose purpose);

    @Modifying
    @Query("UPDATE EmailOtpEntity o SET o.attempts = o.attempts + 1 WHERE o.id = :id AND o.attempts < :maxAttempts")
    int incrementAttempts(@Param("id") Long id, @Param("maxAttempts") int maxAttempts);

    @Modifying
    @Query("UPDATE EmailOtpEntity o SET o.used = true WHERE o.id = :id AND o.used = false")
    int markUsed(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM EmailOtpEntity o WHERE o.expiresAt < :now OR (o.used = true AND o.createdAt < :cutoff)")
    void deleteExpiredAndUsed(@Param("now") Instant now, @Param("cutoff") Instant cutoff);
}