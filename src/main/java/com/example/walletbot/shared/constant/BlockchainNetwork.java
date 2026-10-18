package com.example.walletbot.shared.constant;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public enum BlockchainNetwork {
    ETH("ETH", "https://etherscan.io", true, 1L, 18, "0.0025", "1000000000"),
    BNB("BNB", "https://bscscan.com", true, 56L, 18, "0.0016", "1000000000"),
    BTC("BTC", "https://blockstream.info", false, 0L, 8, "0.00014", "21000000"),
    SOL("SOL", "https://solscan.io", false, 0L, 9, "0.009", "1000000000"),
    TON("GRAM", "https://tonscan.org", false, 0L, 9, "0.1", "1000000000"),
    TRX("TRX", "https://tronscan.org", false, 0L, 6, "4", "1000000000"),

    USDT_ERC20("USDT", "https://etherscan.io", false, 1L, 6, "5", "1000000000"),
    USDT_TRC20("USDT", "https://tronscan.org", false, 0L, 6, "15", "1000000000"),
    USDT_TON("USDT", "https://tonscan.org", false, 0L, 6, "0.1", "1000000000"),
    USDT_SOL("USDT", "https://solscan.io", false, 0L, 6, "1", "1000000000"),
    USDT_BEP20("USDT", "https://bscscan.com", false, 56L, 18, "1", "1000000000"),
    USDC_ERC20("USDC", "https://etherscan.io", false, 1L, 6, "10", "1000000000"),
    USDC_SOL("USDC", "https://solscan.io", false, 0L, 6, "1", "1000000000"),
    USDC_BEP20("USDC", "https://bscscan.com", false, 56L, 18, "1", "1000000000");

    private final String symbol;
    private final String explorerUrl;
    private final boolean evm;
    private final long chainId;
    private final int decimals;
    private final BigDecimal minSend;
    private final BigDecimal maxSend;

    BlockchainNetwork(String symbol, String explorerUrl,
                      boolean evm, long chainId,
                      int decimals, String minSend, String maxSend) {
        this.symbol = symbol;
        this.explorerUrl = explorerUrl;
        this.evm = evm;
        this.chainId = chainId;
        this.decimals = decimals;
        this.minSend = new BigDecimal(minSend);
        this.maxSend = new BigDecimal(maxSend);
    }

    public BlockchainNetwork getParentNetwork() {
        return switch (this) {
            case USDT_ERC20, USDC_ERC20 -> ETH;
            case USDT_BEP20, USDC_BEP20 -> BNB;
            case USDT_TRC20 -> TRX;
            case USDT_TON -> TON;
            case USDT_SOL, USDC_SOL -> SOL;
            case ETH, BNB, BTC, SOL, TON, TRX -> null;
        };
    }

    public Asset getAsset() {
        return switch (this) {
            case BTC -> Asset.BTC;
            case ETH -> Asset.ETH;
            case BNB -> Asset.BNB;
            case SOL -> Asset.SOL;
            case TON -> Asset.TON;
            case TRX -> Asset.TRX;
            case USDT_ERC20, USDT_TRC20, USDT_TON, USDT_SOL, USDT_BEP20 -> Asset.USDT;
            case USDC_ERC20, USDC_SOL, USDC_BEP20 -> Asset.USDC;
        };
    }

    public boolean isBaseNetwork() {
        return getParentNetwork() == null;
    }

    public boolean isToken() {
        return getParentNetwork() != null;
    }

    public boolean isStablecoin() {
        Asset asset = getAsset();
        return asset == Asset.USDT || asset == Asset.USDC;
    }

    public BlockchainNetwork getGasNetwork() {
        BlockchainNetwork parent = getParentNetwork();
        return parent != null ? parent : this;
    }

    public String getAddressHintKey() {
        return switch (getGasNetwork()) {
            case BTC -> "wallet.send.hint.btc";
            case ETH, BNB -> "wallet.send.hint.evm";
            case SOL -> "wallet.send.hint.sol";
            case TON -> "wallet.send.hint.ton";
            case TRX -> "wallet.send.hint.trx";
            default -> throw new IllegalStateException("No hint key for: " + this);
        };
    }

    public String getButtonLabel() {
        return switch (this) {
            case USDT_ERC20 -> "USDT (ERC-20)";
            case USDT_TRC20 -> "USDT (TRC-20)";
            case USDT_TON -> "USDT (TON)";
            case USDT_SOL -> "USDT (SOL)";
            case USDT_BEP20 -> "USDT (BEP-20)";
            case USDC_ERC20 -> "USDC (ERC-20)";
            case USDC_SOL -> "USDC (SOL)";
            case USDC_BEP20 -> "USDC (BEP-20)";
            default -> symbol;
        };
    }

    public String getNetworkLabel() {
        return switch (this) {
            case ETH -> "Ethereum";
            case BNB -> "BNB Smart Chain";
            case BTC -> "Bitcoin";
            case SOL -> "Solana";
            case TON, USDT_TON -> "TON";
            case TRX -> "Tron";
            case USDT_ERC20, USDC_ERC20 -> "Ethereum (ERC-20)";
            case USDT_BEP20, USDC_BEP20 -> "BNB Smart Chain (BEP-20)";
            case USDT_TRC20 -> "Tron (TRC-20)";
            case USDT_SOL, USDC_SOL -> "Solana (SPL)";
        };
    }

}