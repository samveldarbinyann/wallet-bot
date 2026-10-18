package com.example.walletbot.platform.blockchain.util;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EvmAbiEncoderTest {

    @Test
    void encodeBalanceOf_selectorPlusPaddedAddress() {
        String encoded = EvmAbiEncoder.encodeBalanceOf("0x1234567890abcdef1234567890abcdef12345678");

        assertThat(encoded).startsWith(EvmAbiEncoder.BALANCE_OF_SELECTOR);
        assertThat(encoded).hasSize(10 + 64);
        assertThat(encoded).endsWith("0000000000000000000000001234567890abcdef1234567890abcdef12345678");
    }

    @Test
    void encodeTransfer_recipientThenAmount() {
        String encoded = EvmAbiEncoder.encodeTransfer(
                "0x1234567890abcdef1234567890abcdef12345678", BigInteger.valueOf(255));

        assertThat(encoded).startsWith(EvmAbiEncoder.TRANSFER_SELECTOR);
        assertThat(encoded).hasSize(10 + 64 + 64);
        assertThat(encoded).endsWith("00000000000000000000000000000000000000000000000000000000000000ff");
    }

    @Test
    void encodeBalanceOf_acceptsAddressWithoutHexPrefix() {
        String withPrefix = EvmAbiEncoder.encodeBalanceOf("0xabc");
        String without = EvmAbiEncoder.encodeBalanceOf("abc");

        assertThat(withPrefix).isEqualTo(without);
    }

    @Test
    void leftPad64_padsShortHex() {
        assertThat(EvmAbiEncoder.leftPad64("ff")).hasSize(64).endsWith("ff").startsWith("00");
    }

    @Test
    void leftPad64_leavesLongHexUnchanged() {
        String full = "a".repeat(64);
        assertThat(EvmAbiEncoder.leftPad64(full)).isEqualTo(full);
    }
}
