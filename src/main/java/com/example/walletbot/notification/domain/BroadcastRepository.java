package com.example.walletbot.notification.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BroadcastRepository extends JpaRepository<BroadcastEntity, Long> {

    Page<BroadcastEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BroadcastEntity b WHERE b.id = ?1")
    Optional<BroadcastEntity> findByIdForUpdate(Long id);

    @Query("SELECT b.imageUrl FROM BroadcastEntity b WHERE b.imageUrl IS NOT NULL")
    List<String> findAllImageUrls();
}
