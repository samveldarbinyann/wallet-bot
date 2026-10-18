package com.example.walletbot.wallet.domain;

import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AddressPolicyService {

    private final BlockchainServiceFactory blockchainFactory;

    public void requireValid(BlockchainNetwork network, String address) {
        if (address == null || address.isBlank()) {
            throw new BadRequestException("error.address_required");
        }
        if (!blockchainFactory.getService(network).isValidAddress(address.trim())) {
            throw new BadRequestException("error.invalid_address_for_network", network.getButtonLabel());
        }
    }
}