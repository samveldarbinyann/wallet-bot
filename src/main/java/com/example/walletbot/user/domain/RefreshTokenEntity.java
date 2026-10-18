package com.example.walletbot.user.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                @Index(name = "ix_refresh_tokens_user_id", columnList = "user_id"),
                @Index(name = "ix_refresh_tokens_expires_at", columnList = "expires_at")
        }
)
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    void prePersist() {
        if (issuedAt == null) issuedAt = Instant.now();
    }
}
