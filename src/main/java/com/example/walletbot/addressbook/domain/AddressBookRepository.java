package com.example.walletbot.addressbook.domain;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressBookRepository extends JpaRepository<AddressBookEntity, Long> {

    List<AddressBookEntity> findByUserIdOrderByLastUsedDesc(Long userId);

    Optional<AddressBookEntity> findByUserIdAndNetworkAndAddress(Long userId, BlockchainNetwork network, String address);

    long countByUserId(Long userId);
}
