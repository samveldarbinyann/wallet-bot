package com.example.walletbot.user.domain;

import com.example.walletbot.platform.persistence.EncryptionConverter;
import com.example.walletbot.shared.constant.*;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    private String username;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "userEntity", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @ToString.Exclude
    private List<WalletEntity> wallets = new ArrayList<>();

    @Column(nullable = false)
    private String lang = SupportedLanguage.DEFAULT.name();

    @Column(length = 10)
    private String currency = SupportedCurrency.DEFAULT.name();

    @Column(length = 50)
    private String timezone = "UTC";

    @Column(length = 16, nullable = false)
    private String theme = UiTheme.DEFAULT.name();

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "email", length = 500)
    @ToString.Exclude
    private String email;

    @Column(name = "email_hash", length = 64)
    @ToString.Exclude
    private String emailHash;

    @Column(name = "email_verified_at")
    @ToString.Exclude
    private Instant emailVerifiedAt;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "pending_email", length = 500)
    @ToString.Exclude
    private String pendingEmail;

    @JsonIgnore
    @ToString.Exclude
    @Column(name = "pin_hash")
    private String pinHash;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;


    @Column(name = "pin_failures", nullable = false)
    private int pinFailures = 0;

    @Column(name = "pin_locked_until")
    private Instant pinLockedUntil;

    @Column(name = "pin_enabled", nullable = false)
    private boolean pinEnabled = false;

    @Column(name = "two_fa_login_enabled", nullable = false)
    private boolean twoFaLoginEnabled = false;

    @Column(name = "notify_push_enabled", nullable = false)
    private boolean notifyPushEnabled = true;

    @Column(name = "notify_email_enabled", nullable = false)
    private boolean notifyEmailEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "status_changed_by")
    private Long statusChangedBy;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public boolean isEmailDeliverable() {
        return email != null && emailVerifiedAt != null;
    }
}
