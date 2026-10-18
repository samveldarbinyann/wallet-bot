package com.example.walletbot.swap.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "swap_orders")
public class SwapOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "from_wallet_id", nullable = false)
    private Long fromWalletId;

    @Column(name = "to_wallet_id", nullable = false)
    private Long toWalletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_network", length = 20, nullable = false)
    private BlockchainNetwork fromNetwork;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_network", length = 20, nullable = false)
    private BlockchainNetwork toNetwork;

    @Column(name = "from_address", length = 256)
    private String fromAddress;

    @Column(name = "to_address", length = 256, nullable = false)
    private String toAddress;

    @Column(name = "amount_in", precision = 30, scale = 18, nullable = false)
    private BigDecimal amountIn;

    @Column(name = "amount_out_expected", precision = 30, scale = 18)
    private BigDecimal amountOutExpected;

    @Column(name = "amount_out_min", precision = 30, scale = 18)
    private BigDecimal amountOutMin;

    @Column(name = "amount_out_actual", precision = 30, scale = 18)
    private BigDecimal amountOutActual;

    @Column(name = "rate_quoted", precision = 30, scale = 18)
    private BigDecimal rateQuoted;

    @Column(name = "provider", length = 32, nullable = false)
    private String provider;

    @Column(name = "provider_quote_id", length = 128)
    private String providerQuoteId;

    @Column(name = "provider_order_id", length = 128)
    private String providerOrderId;

    @Column(name = "deposit_address", length = 256)
    private String depositAddress;

    @Column(name = "source_tx_hash", length = 128)
    private String sourceTxHash;

    @Column(name = "dest_tx_hash", length = 128)
    private String destTxHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 24, nullable = false)
    private SwapOrderStatus status;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "idem_key", length = 128, nullable = false)
    private String idemKey;

    @Column(name = "intent_jti", length = 64, nullable = false)
    private String intentJti;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static String truncateReason(String reason) {
        if (reason == null) return null;
        return reason.length() > 480 ? reason.substring(0, 480) : reason;
    }
}
