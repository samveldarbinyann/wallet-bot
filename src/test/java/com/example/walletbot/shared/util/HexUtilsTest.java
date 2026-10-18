package com.example.walletbot.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HexUtilsTest {

    @Test
    void bytesToHex_knownInput_knownOutput() {
        assertThat(HexUtils.bytesToHex(new byte[]{0x00, 0x0f, (byte) 0xff, 0x10}))
                .isEqualTo("000fff10");
    }

    @Test
    void bytesToHex_emptyArray_emptyString() {
        assertThat(HexUtils.bytesToHex(new byte[0])).isEmpty();
    }

    @Test
    void hexToBytes_knownInput_knownOutput() {
        assertThat(HexUtils.hexToBytes("000fff10"))
                .containsExactly(0x00, 0x0f, (byte) 0xff, 0x10);
    }

    @Test
    void hexToBytes_mixedCase_parsed() {
        assertThat(HexUtils.hexToBytes("AbCd")).containsExactly((byte) 0xab, (byte) 0xcd);
    }

    @Test
    void roundTrip_bytesToHexToBytes_isIdentity() {
        byte[] original = {1, 2, 3, (byte) 200, (byte) 255, 0, 42};
        assertThat(HexUtils.hexToBytes(HexUtils.bytesToHex(original))).containsExactly(
                1, 2, 3, (byte) 200, (byte) 255, 0, 42);
    }
}