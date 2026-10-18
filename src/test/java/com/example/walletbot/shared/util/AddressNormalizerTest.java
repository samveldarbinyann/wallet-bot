package com.example.walletbot.shared.util;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressNormalizerTest {

    private static final String EVM_CHECKSUM = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
    private static final String EVM_LOWER = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed";

    private final AddressNormalizer normalizer = new AddressNormalizer();

    @Test
    void canonical_null_returnsNull() {
        assertThat(normalizer.canonical(BlockchainNetwork.ETH, null)).isNull();
    }

    @Test
    void canonical_blank_throwsBadRequest() {
        assertThatThrownBy(() -> normalizer.canonical(BlockchainNetwork.ETH, "   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.address_empty");
    }

    @Test
    void canonical_evm_lowercaseInput_isChecksummed() {
        assertThat(normalizer.canonical(BlockchainNetwork.ETH, "  " + EVM_LOWER + "  "))
                .isEqualTo(EVM_CHECKSUM);
    }

    @Test
    void canonical_evmToken_usesParentEvmChecksum() {
        assertThat(normalizer.canonical(BlockchainNetwork.USDT_ERC20, EVM_LOWER))
                .isEqualTo(EVM_CHECKSUM);
    }

    @Test
    void canonical_ton_invalidAddress_throwsBadRequest() {
        assertThatThrownBy(() -> normalizer.canonical(BlockchainNetwork.TON, "not-a-ton-address"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.invalid_ton_address");
    }

    @Test
    void canonical_default_trimsOnly() {
        String btc = "1BoatSLRHtKNngkdXEeobR76b53LETtpyT";
        assertThat(normalizer.canonical(BlockchainNetwork.BTC, "  " + btc + "  ")).isEqualTo(btc);
    }

    @Test
    void sameAddress_evmCaseInsensitive_returnsTrue() {
        assertThat(normalizer.sameAddress(BlockchainNetwork.ETH, EVM_LOWER, EVM_CHECKSUM)).isTrue();
    }

    @Test
    void sameAddress_differentAddresses_returnsFalse() {
        assertThat(normalizer.sameAddress(BlockchainNetwork.BTC, "addrA", "addrB")).isFalse();
    }

    @Test
    void sameAddress_invalidInput_returnsFalse() {
        assertThat(normalizer.sameAddress(BlockchainNetwork.ETH, "0xbad", EVM_CHECKSUM)).isFalse();
    }
}