package com.example.walletbot.transaction.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(
        name = "send_idempotency",
        uniqueConstraints = @UniqueConstraint(name = "uq_send_idempotency_user_key", columnNames = {"user_id", "idem_key"})
)
public class SendIdempotencyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idem_key", nullable = false, length = 80)
    private String idemKey;

    @Column(name = "tx_hash", length = 200)
    private String txHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
