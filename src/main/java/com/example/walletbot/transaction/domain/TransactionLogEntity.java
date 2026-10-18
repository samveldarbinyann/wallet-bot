package com.example.walletbot.transaction.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Data
@Entity
@Table(name = "transaction_logs", uniqueConstraints = @UniqueConstraint(name = "uq_wallet_txhash", columnNames = {"wallet_id", "tx_hash"}))
public class TransactionLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "wallet_id")
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private TxType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private BlockchainNetwork network;

    @Column(name = "from_address", length = 256)
    private String fromAddress;

    @Column(name = "to_address", length = 256)
    private String toAddress;

    @Column(precision = 30, scale = 18)
    private BigDecimal amount;

    @Column(length = 20)
    private String symbol;

    @Column(name = "tx_hash", length = 128)
    private String txHash;

    @Column(name = "external_ref", length = 128)
    private String externalRef;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private TxStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "tx_timestamp")
    private LocalDateTime txTimestamp;

    @Column(name = "usd_value", precision = 20, scale = 2)
    private BigDecimal usdValue;

    @Column(name = "fee_usd", precision = 20, scale = 2)
    private BigDecimal feeUsd;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public LocalDateTime getDisplayTime() {
        return txTimestamp != null ? txTimestamp : createdAt;
    }

    public enum TxType {
        SEND, SWAP, DEPOSIT
    }

    public enum TxStatus {
        PENDING, SUCCESS, FAILED
    }
}
