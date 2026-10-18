package com.example.walletbot.platform.web.validation;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NetworkParser {

    private static final Map<String, BlockchainNetwork> SWAP_ALIASES = Map.of(
            "USDT", BlockchainNetwork.USDT_TRC20,
            "USDC", BlockchainNetwork.USDC_ERC20
    );

    public BlockchainNetwork parseStrict(String raw) {
        return parseOrThrow(raw, false);
    }

    public BlockchainNetwork parseForSwap(String raw) {
        return parseOrThrow(raw, true);
    }

    private BlockchainNetwork parseOrThrow(String raw, boolean allowAliases) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("error.network_required");
        }
        String norm = raw.trim().toUpperCase();
        if (allowAliases) {
            BlockchainNetwork alias = SWAP_ALIASES.get(norm);
            if (alias != null) return alias;
        }
        try {
            return BlockchainNetwork.valueOf(norm);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("error.unknown_network", norm);
        }
    }
}