package com.example.walletbot.user.domain;

import com.example.walletbot.shared.constant.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByTelegramId(Long telegramId);

    @Query("SELECT u FROM UserEntity u WHERE LOWER(u.username) = LOWER(:username)")
    Optional<UserEntity> findByUsernameIgnoreCase(@Param("username") String username);

    @Modifying
    @Query("""
            UPDATE UserEntity u
            SET u.username = NULL
            WHERE LOWER(u.username) = LOWER(:username)
            AND (:exceptUserId IS NULL OR u.id <> :exceptUserId)
            """)
    int clearUsername(@Param("username") String username, @Param("exceptUserId") Long exceptUserId);

    Page<UserEntity> findByEmailIsNotNullAndEmailVerifiedAtIsNotNull(Pageable pageable);

    @Query(
            value = """
                        SELECT u FROM UserEntity u
                        WHERE (:status IS NULL OR u.status = :status)
                        AND (:pattern IS NULL
                             OR CAST(u.telegramId AS string) LIKE :pattern
                             OR LOWER(u.username) LIKE :pattern)
                    """,
            countQuery = """
                        SELECT COUNT(u) FROM UserEntity u
                        WHERE (:status IS NULL OR u.status = :status)
                        AND (:pattern IS NULL
                             OR CAST(u.telegramId AS string) LIKE :pattern
                             OR LOWER(u.username) LIKE :pattern)
                    """
    )
    Page<UserEntity> findAdminUsers(
            @Param("status") UserStatus status,
            @Param("pattern") String pattern,
            Pageable pageable);

    @Query("SELECT u.status, COUNT(u) FROM UserEntity u GROUP BY u.status")
    List<Object[]> countUsersByStatus();

    @Query("SELECT u.id FROM UserEntity u WHERE u.lastLoginAt >= :threshold")
    List<Long> findUserIdsActiveSince(@Param("threshold") Instant threshold);

    @Query("SELECT u.id FROM UserEntity u WHERE u.lastLoginAt IS NULL OR u.lastLoginAt < :threshold")
    List<Long> findUserIdsInactiveBefore(@Param("threshold") Instant threshold);

    @Modifying
    @Query("""
            UPDATE UserEntity u
            SET u.pinFailures = u.pinFailures + 1,
                u.pinLockedUntil = CASE WHEN u.pinFailures + 1 >= :maxAttempts THEN :lockUntil ELSE u.pinLockedUntil END
            WHERE u.id = :userId
            """)
    int incrementPinFailures(@Param("userId") Long userId,
                             @Param("maxAttempts") int maxAttempts,
                             @Param("lockUntil") Instant lockUntil);

    @Modifying
    @Query("UPDATE UserEntity u SET u.pinFailures = 0, u.pinLockedUntil = NULL WHERE u.id = :userId AND (u.pinFailures > 0 OR u.pinLockedUntil IS NOT NULL)")
    int resetPinFailures(@Param("userId") Long userId);
}