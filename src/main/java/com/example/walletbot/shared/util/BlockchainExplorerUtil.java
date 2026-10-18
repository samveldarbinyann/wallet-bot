package com.example.walletbot.shared.util;

import com.example.walletbot.shared.constant.BlockchainNetwork;

public class BlockchainExplorerUtil {

    public static String getTxUrl(BlockchainNetwork network, String txHash) {
        if (txHash == null || txHash.isBlank()) {
            return null;
        }

        return switch (network) {
            case BTC -> "https://mempool.space/tx/" + txHash;
            case ETH -> "https://etherscan.io/tx/" + txHash;
            case BNB -> "https://bscscan.com/tx/" + txHash;
            case SOL -> "https://solscan.io/tx/" + txHash;
            case TON -> "https://tonviewer.com/transaction/" + txHash;
            case TRX -> "https://tronscan.org/#/transaction/" + txHash;
            case USDT_ERC20 -> "https://etherscan.io/tx/" + txHash;
            case USDC_ERC20 -> "https://etherscan.io/tx/" + txHash;
            case USDT_BEP20, USDC_BEP20 -> "https://bscscan.com/tx/" + txHash;
            case USDT_TRC20 -> "https://tronscan.org/#/transaction/" + txHash;
            case USDT_TON -> "https://tonviewer.com/transaction/" + txHash;
            case USDT_SOL -> "https://solscan.io/tx/" + txHash;
            case USDC_SOL -> "https://solscan.io/tx/" + txHash;
        };
    }
}
