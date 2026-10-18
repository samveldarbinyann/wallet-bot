package com.example.walletbot.user.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "email_otp")
@Data
@NoArgsConstructor
public class EmailOtpEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "context")
    private String context;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public enum OtpPurpose {
        LOGIN,
        EMAIL_BIND,
        EMAIL_UNLINK,
        PIN_SETUP,
        PIN_RESET,
        TWOFA_ENABLE,
        TWOFA_DISABLE,
        EXPORT_KEYS
    }
}