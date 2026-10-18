package com.example.walletbot.shared.util;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.ton.java.address.Address;
import org.web3j.crypto.Keys;

@Component
public class AddressNormalizer {

    public String canonical(BlockchainNetwork network, String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("error.address_empty");
        }
        return switch (network.getGasNetwork()) {
            case ETH, BNB -> toEvmChecksum(trimmed);
            case TON -> toTonFriendly(trimmed);
            default -> trimmed;
        };
    }

    public boolean sameAddress(BlockchainNetwork network, String a, String b) {
        try {
            String ca = canonical(network, a);
            String cb = canonical(network, b);
            return ca != null && ca.equals(cb);
        } catch (BadRequestException e) {
            return false;
        }
    }

    private String toEvmChecksum(String hex) {
        try {
            return Keys.toChecksumAddress(hex);
        } catch (Exception e) {
            throw new BadRequestException("error.invalid_evm_address");
        }
    }

    private String toTonFriendly(String addr) {
        try {
            return Address.of(addr).toNonBounceable();
        } catch (Exception | Error e) {
            throw new BadRequestException("error.invalid_ton_address");
        }
    }
}