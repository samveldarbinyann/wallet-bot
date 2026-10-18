package com.example.walletbot.export.application;

import com.example.walletbot.shared.constant.BlockchainNetwork;

import java.util.List;

public final class ExportNetworkCoverage {

    private ExportNetworkCoverage() {
    }

    public static boolean isExportable(BlockchainNetwork network) {
        return !network.isToken() && network != BlockchainNetwork.BNB;
    }

    public static List<String> coversFor(BlockchainNetwork network) {
        return switch (network) {
            case ETH -> List.of("BNB", "USDT_ERC20", "USDC_ERC20", "USDT_BEP20", "USDC_BEP20");
            case TON -> List.of("USDT_TON");
            case TRX -> List.of("USDT_TRC20");
            case SOL -> List.of("USDT_SOL", "USDC_SOL");
            default -> List.of();
        };
    }

    public static String formatFor(BlockchainNetwork network) {
        return switch (network) {
            case BTC -> "WIF";
            case SOL -> "base58";
            default -> "hex";
        };
    }
}