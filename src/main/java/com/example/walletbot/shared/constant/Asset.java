package com.example.walletbot.shared.constant;

import lombok.Getter;

@Getter
public enum Asset {
    BTC("Bitcoin", "BTC"),
    ETH("Ethereum", "ETH"),
    BNB("BNB", "BNB"),
    SOL("Solana", "SOL"),
    TON("Gram", "GRAM"),
    TRX("Tron", "TRX"),
    USDT("Tether", "USDT"),
    USDC("USD Coin", "USDC");

    private final String displayName;
    private final String symbol;

    Asset(String displayName, String symbol) {
        this.displayName = displayName;
        this.symbol = symbol;
    }
}