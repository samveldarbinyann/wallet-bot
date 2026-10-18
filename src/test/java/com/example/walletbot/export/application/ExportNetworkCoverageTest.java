package com.example.walletbot.export.application;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExportNetworkCoverageTest {

    @Test
    void isExportable_baseNetworksExceptBnb() {
        assertThat(ExportNetworkCoverage.isExportable(BlockchainNetwork.ETH)).isTrue();
        assertThat(ExportNetworkCoverage.isExportable(BlockchainNetwork.BTC)).isTrue();
        assertThat(ExportNetworkCoverage.isExportable(BlockchainNetwork.BNB)).isFalse();
    }

    @Test
    void isExportable_tokensNotExportable() {
        assertThat(ExportNetworkCoverage.isExportable(BlockchainNetwork.USDT_ERC20)).isFalse();
        assertThat(ExportNetworkCoverage.isExportable(BlockchainNetwork.USDT_TON)).isFalse();
    }

    @Test
    void coversFor_ethCoversBnbAndErcTokens() {
        assertThat(ExportNetworkCoverage.coversFor(BlockchainNetwork.ETH))
                .containsExactly("BNB", "USDT_ERC20", "USDC_ERC20", "USDT_BEP20", "USDC_BEP20");
    }

    @Test
    void coversFor_tonAndTrx() {
        assertThat(ExportNetworkCoverage.coversFor(BlockchainNetwork.TON)).containsExactly("USDT_TON");
        assertThat(ExportNetworkCoverage.coversFor(BlockchainNetwork.TRX)).containsExactly("USDT_TRC20");
    }

    @Test
    void coversFor_btcCoversNothing() {
        assertThat(ExportNetworkCoverage.coversFor(BlockchainNetwork.BTC)).isEmpty();
    }

    @Test
    void formatFor_byNetwork() {
        assertThat(ExportNetworkCoverage.formatFor(BlockchainNetwork.BTC)).isEqualTo("WIF");
        assertThat(ExportNetworkCoverage.formatFor(BlockchainNetwork.SOL)).isEqualTo("base58");
        assertThat(ExportNetworkCoverage.formatFor(BlockchainNetwork.ETH)).isEqualTo("hex");
        assertThat(ExportNetworkCoverage.formatFor(BlockchainNetwork.TON)).isEqualTo("hex");
    }
}
