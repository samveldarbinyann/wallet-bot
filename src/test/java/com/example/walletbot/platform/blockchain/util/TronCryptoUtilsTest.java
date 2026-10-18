package com.example.walletbot.platform.blockchain.util;

import com.example.walletbot.shared.util.HexUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TronCryptoUtilsTest {

    private static final String PRIV_HEX = "0000000000000000000000000000000000000000000000000000000000000001";

    @Test
    void deriveAddress_isDeterministicMainnetAddress() {
        String addr = TronCryptoUtils.getAddressFromPrivateKey(PRIV_HEX);

        assertThat(addr).startsWith("T");
        assertThat(TronCryptoUtils.getAddressFromPrivateKey(PRIV_HEX)).isEqualTo(addr);
    }

    @Test
    void addressRoundTrip_base58ToHexAndBack() {
        String addr = TronCryptoUtils.getAddressFromPrivateKey(PRIV_HEX);

        String hex20 = TronCryptoUtils.tronAddressToHex(addr);
        assertThat(hex20).hasSize(40);

        byte[] payload = new byte[21];
        payload[0] = TronCryptoUtils.TRON_MAINNET_PREFIX;
        System.arraycopy(HexUtils.hexToBytes(hex20), 0, payload, 1, 20);
        assertThat(TronCryptoUtils.base58CheckEncode(payload)).isEqualTo(addr);
    }

    @Test
    void base58CheckDecode_invalidSymbol_throws() {
        assertThatThrownBy(() -> TronCryptoUtils.base58CheckDecode("0OIl"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sha256_knownVector() {
        byte[] hash = TronCryptoUtils.sha256("abc".getBytes(StandardCharsets.UTF_8));
        assertThat(HexUtils.bytesToHex(hash))
                .isEqualToIgnoringCase("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void keccak256_knownEmptyVector() {
        byte[] hash = TronCryptoUtils.keccak256(new byte[0]);
        assertThat(HexUtils.bytesToHex(hash))
                .isEqualToIgnoringCase("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470");
    }

    @Test
    void signData_produces65ByteSignature() {
        byte[] digest = TronCryptoUtils.sha256("hello".getBytes(StandardCharsets.UTF_8));
        byte[] sig = TronCryptoUtils.signData(digest, PRIV_HEX);

        assertThat(sig).hasSize(65);
        assertThat(sig[64]).isBetween((byte) 0, (byte) 3);
    }
}
