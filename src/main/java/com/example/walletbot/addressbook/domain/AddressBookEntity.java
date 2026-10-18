package com.example.walletbot.addressbook.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "address_book",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "network", "address"}))
public class AddressBookEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private BlockchainNetwork network;

    @Column(length = 256, nullable = false)
    private String address;

    @Column(length = 100)
    private String label;

    private LocalDateTime lastUsed;

    @PrePersist
    protected void onCreate() {
        lastUsed = LocalDateTime.now();
    }
}
