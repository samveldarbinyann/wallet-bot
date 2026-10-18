package com.example.walletbot.shared.constant;

import lombok.Getter;

@Getter
public enum TokenInfo {

    USDT("Tether", "USDT",
            new TokenContract[]{
                    new TokenContract(BlockchainNetwork.USDT_ERC20, "0xdAC17F958D2ee523a2206206994597C13D831ec7"),
                    new TokenContract(BlockchainNetwork.USDT_TON, "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"),
                    new TokenContract(BlockchainNetwork.USDT_TRC20, "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"),
                    new TokenContract(BlockchainNetwork.USDT_SOL, "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"),
                    new TokenContract(BlockchainNetwork.USDT_BEP20, "0x55d398326f99059fF775485246999027B3197955"),
            }),

    USDC("USD Coin", "USDC",
            new TokenContract[]{
                    new TokenContract(BlockchainNetwork.USDC_ERC20, "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"),
                    new TokenContract(BlockchainNetwork.USDC_SOL, "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"),
                    new TokenContract(BlockchainNetwork.USDC_BEP20, "0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d"),
            });

    private final String displayName;
    private final String symbol;
    private final TokenContract[] contracts;

    TokenInfo(String displayName, String symbol, TokenContract[] contracts) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.contracts = contracts;
    }

    public String getContractAddress(BlockchainNetwork network) {
        for (TokenContract c : contracts) {
            if (c.network() == network)
                return c.address();
        }
        return null;
    }

    public record TokenContract(BlockchainNetwork network, String address) {
    }
}