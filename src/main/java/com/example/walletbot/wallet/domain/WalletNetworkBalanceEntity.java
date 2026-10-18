package com.example.walletbot.wallet.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "wallet_network_balances",
        uniqueConstraints = @UniqueConstraint(name = "uk_wallet_network_balances_wallet_network",
                columnNames = {"wallet_id", "network"}))
public class WalletNetworkBalanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "network", length = 30, nullable = false)
    private BlockchainNetwork network;

    @Column(name = "balance", length = 72, nullable = false)
    private String balance;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
