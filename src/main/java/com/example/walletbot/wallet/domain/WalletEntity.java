package com.example.walletbot.wallet.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.platform.persistence.EncryptionConverter;
import com.example.walletbot.user.domain.UserEntity;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "wallets")
public class WalletEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity userEntity;

    @Column(nullable = false)
    private String address;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "private_key", length = 1000, nullable = false)
    private String privateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "network", length = 20, nullable = false)
    private BlockchainNetwork network;

    @Column(name = "last_deposit_scan_at")
    private LocalDateTime lastDepositScanAt;
}
