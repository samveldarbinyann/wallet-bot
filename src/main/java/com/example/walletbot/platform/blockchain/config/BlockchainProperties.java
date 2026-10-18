package com.example.walletbot.platform.blockchain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("blockchain")
public record BlockchainProperties(
        Btc btc,
        Ton ton,
        Token token,
        Evm evm
) {
    public record Btc(String network, String mempoolBaseUrl) {
    }

    public record Ton(int walletId, String transferComment, long txValidUntilSeconds, int rateLimitPerSecond) {
    }

    public record Token(
            String usdtErc20Contract,
            String usdcErc20Contract,
            String usdtBep20Contract,
            String usdcBep20Contract,
            String usdtTrc20Contract,
            String usdtTonJettonMaster,
            String usdtSolMint,
            String usdcSolMint
    ) {
    }

    public record Evm(long ethChainId, long bnbChainId, boolean strictChecksum) {
    }
}