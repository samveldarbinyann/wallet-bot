package com.example.walletbot.platform.web.validation;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkParserTest {

    private NetworkParser parser;

    @BeforeEach
    void setUp() {
        parser = new NetworkParser();
    }

    @Test
    void parseStrict_validLowercaseTrimmed_resolves() {
        assertThat(parser.parseStrict("  eth ")).isEqualTo(BlockchainNetwork.ETH);
    }

    @Test
    void parseStrict_null_throws() {
        assertThatThrownBy(() -> parser.parseStrict(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.network_required");
    }

    @Test
    void parseStrict_blank_throws() {
        assertThatThrownBy(() -> parser.parseStrict("   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.network_required");
    }

    @Test
    void parseStrict_unknown_throws() {
        assertThatThrownBy(() -> parser.parseStrict("DOGEZILLA"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.unknown_network");
    }

    @Test
    void parseStrict_doesNotAcceptSwapAliases() {
        assertThatThrownBy(() -> parser.parseStrict("USDT"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.unknown_network");
    }

    @Test
    void parseForSwap_usdtAlias_resolvesToTrc20() {
        assertThat(parser.parseForSwap("usdt")).isEqualTo(BlockchainNetwork.USDT_TRC20);
    }

    @Test
    void parseForSwap_usdcAlias_resolvesToErc20() {
        assertThat(parser.parseForSwap("USDC")).isEqualTo(BlockchainNetwork.USDC_ERC20);
    }

    @Test
    void parseForSwap_regularNetwork_resolves() {
        assertThat(parser.parseForSwap("btc")).isEqualTo(BlockchainNetwork.BTC);
    }
}
