package com.example.walletbot.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    Page<NotificationEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<NotificationEntity> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadFalse(Long userId);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.read = true WHERE n.userId = ?1 AND n.read = false")
    void markAllAsRead(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.read = true WHERE n.id = ?1 AND n.userId = ?2 AND n.read = false")
    void markAsRead(@Param("id") Long id, @Param("userId") Long userId);
}
