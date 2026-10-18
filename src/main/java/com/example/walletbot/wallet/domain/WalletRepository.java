package com.example.walletbot.wallet.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<WalletEntity, Long> {

    List<WalletEntity> findByUserEntityId(Long userId);

    boolean existsByUserEntityId(Long userId);

    Optional<WalletEntity> findByIdAndUserEntityId(Long id, Long userId);

    Optional<WalletEntity> findByAddressIgnoreCaseAndNetwork(String address, BlockchainNetwork network);

    @Query("select w.address from WalletEntity w where w.network = ?1")
    List<String> findAddressesByNetwork(BlockchainNetwork network);
}
